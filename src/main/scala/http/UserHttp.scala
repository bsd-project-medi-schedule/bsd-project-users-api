package http

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.toBifunctorOps
import config.objects.NetworkConfig
import error.ServiceError
import io.circe.syntax.*
import io.circe.Json

import java.util.UUID
import nats.EventBus
import nats.NatsEvent
import natstools.events.EmailEvent
import natstools.events.UserCreatedEvent
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.dsl.io.*
import org.http4s.HttpRoutes
import service.UserService
import utils.memory.ConfirmUsersToken
import utils.memory.NewUsersTokens
import utils.JwtService
import utils.NonceService
import utils.Sha256Service
import utils.UserRanks
import DTO.GenericMessageDTO
import DTO.UserDTO
import org.http4s.client.Client

final case class UserHttp(
)(implicit
  userService: UserService,
  jwtService: JwtService,
  client: Client[IO],
  eventBus: EventBus,
  networkConfig: NetworkConfig
) {

  private object OffsetMatcher extends QueryParamDecoderMatcher[Int]("offset")
  private object SizeMatcher extends QueryParamDecoderMatcher[Int]("size")

  def routes(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case req @ GET -> Root / "user" =>
        val resData = for {
          (jwtClaims, refreshResult) <-
            HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
          userId = UUID.fromString(jwtClaims.getSubject)

          userDTO <- userService.readUser(userId)
        } yield (userDTO, refreshResult)

        resData.fold(
          err => ErrorMapper.toResponse(err),
          res => {
            val (user, refreshResult) = res
            HttpUtils.handleTokenRefresh(Ok(user.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "user" :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
          resp               <- userService.readUsers(offset, size)
        } yield (resp, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          res => {
            val (users, refreshResult) = res
            HttpUtils.handleTokenRefresh(Ok(users.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "user" / search :? OffsetMatcher(offset) +& SizeMatcher(
            size
          ) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
          resp <-
            userService.readBySearch(
              search,
              offset,
              size
            )
        } yield (resp, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          res => {
            val (users, refreshResult) = res
            HttpUtils.handleTokenRefresh(Ok(users.asJson), refreshResult)
          }
        ).flatten

      case req @ POST -> Root / "user" / "client" =>
        req.as[UserDTO].flatMap { userData =>
          val maybeUserId = for {
            userId <- userService.createUser(userData.copy(role = Some(2)))
            otp = NonceService.generatePin(length = 12)
            _ <- EitherT(ConfirmUsersToken.put(
              Sha256Service.hash(otp),
              userId
            ).attempt.map(_.leftMap(_ =>
              ServiceError.InternalError("There was an internal problem")
            )))

            welcomeEmailMetadata = Map(
              "name" -> s"${userData.firstName} ${userData.lastName}"
            )
            createdEmailMetadata = Map(
              "name" -> s"${userData.firstName} ${userData.lastName}",
              "otp" -> otp
            )

            events = Seq(
              NatsEvent.create[UserCreatedEvent]((id, ts) =>
                UserCreatedEvent(id, ts, userId, userData.email, 2)
              ),
              NatsEvent.create[EmailEvent]((id, ts) =>
                EmailEvent(
                  id,
                  ts,
                  userData.email,
                  purpose = EmailEvent.PURPOSE_WELCOME,
                  welcomeEmailMetadata
                )
              ),
              NatsEvent.create[EmailEvent]((id, ts) =>
                EmailEvent(
                  id,
                  ts,
                  userData.email,
                  purpose = EmailEvent.PURPOSE_CONFIRM,
                  createdEmailMetadata
                )
              )
            )
            _ <- HttpUtils.httpPublishEvent(events, "Email service unavailable")
          } yield userId

          maybeUserId.fold(
            err => ErrorMapper.toResponse(err),
            _ => Ok(Json.obj("message" -> "User created successfully".asJson))
          ).flatten
        }

      case req @ POST -> Root / "user" =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
        } yield refreshResult

        result.fold(
          err => ErrorMapper.toResponse(err),
          refreshResult => {
            val nonce = NonceService.generatePin()
            for {
              _ <- NewUsersTokens.put(Sha256Service.hash(nonce))
              response <-
                HttpUtils.handleTokenRefresh(Ok(Json.obj("nonce" -> nonce.asJson)), refreshResult)
            } yield response
          }
        ).flatten

      case req @ POST -> Root / "user" / otp =>
        req.as[UserDTO].flatMap { userData =>
          val maybeUserId = for {
            _ <- EitherT.fromOptionF(
              NewUsersTokens.remove(Sha256Service.hash(otp)),
              ServiceError.BadRequest("Token not found"): ServiceError
            )
            userId <- userService.createUser(userData.copy(role = Some(1)))
            otp = NonceService.generatePin(length = 12)
            _ <- EitherT(ConfirmUsersToken.put(
              Sha256Service.hash(otp),
              userId
            ).attempt.map(_.leftMap(_ =>
              ServiceError.InternalError("There was an internal problem")
            )))

            welcomeEmailMetadata = Map(
              "name" -> s"${userData.firstName} ${userData.lastName}"
            )
            createdEmailMetadata = Map(
              "name" -> s"${userData.firstName} ${userData.lastName}",
              "otp" -> otp
            )

            events = Seq(
              NatsEvent.create[UserCreatedEvent]((id, ts) =>
                UserCreatedEvent(id, ts, userId, userData.email, 1)
              ),
              NatsEvent.create[EmailEvent]((id, ts) =>
                EmailEvent(
                  id,
                  ts,
                  userData.email,
                  purpose = "email.user.welcome",
                  welcomeEmailMetadata
                )
              ),
              NatsEvent.create[EmailEvent]((id, ts) =>
                EmailEvent(
                  id,
                  ts,
                  userData.email,
                  purpose = "email.user.confirm",
                  createdEmailMetadata
                )
              )
            )
            _ <- HttpUtils.httpPublishEvent(events, "Email service unavailable")
          } yield userId

          maybeUserId.fold(
            err => ErrorMapper.toResponse(err),
            _ => Ok(Json.obj("message" -> "User created successfully".asJson))
          ).flatten
        }

      case GET -> Root / "user" / "confirm" / otp =>
        val resp = for {
          userId <- EitherT.fromOptionF(
            ConfirmUsersToken.remove(Sha256Service.hash(otp)),
            ServiceError.BadRequest("Token not found"): ServiceError
          )
          _ <- userService.confirmUser(userId)
        } yield ()

        resp.fold(
          err => ErrorMapper.toResponse(err),
          _ => Ok(Json.obj("message" -> "User confirmed successfully".asJson))
        ).flatten

      case req @ PUT -> Root / "user" =>
        req.as[UserDTO].flatMap { newUser =>
          val resp = for {
            (jwtClaims, refreshResult) <-
              HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
            userId = UUID.fromString(jwtClaims.getSubject)
            _ <- userService.updateUser(userId, newUser)
          } yield refreshResult

          resp.fold(
            err => ErrorMapper.toResponse(err),
            refreshResult =>
              HttpUtils.handleTokenRefresh(
                Ok(Json.obj("message" -> "User updated successfully".asJson)),
                refreshResult
              )
          ).flatten
        }

      case req @ PUT -> Root / "user" / "password" =>
        req.as[GenericMessageDTO].flatMap { passwordMessage =>
          val resp = for {
            (jwtClaims, refreshResult) <-
              HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
            userId = UUID.fromString(jwtClaims.getSubject)
            _ <- userService.updatePassword(userId, passwordMessage.message)
          } yield refreshResult

          resp.fold(
            err => ErrorMapper.toResponse(err),
            refreshResult =>
              HttpUtils.handleTokenRefresh(
                Ok(Json.obj("message" -> "Password updated successfully".asJson)),
                refreshResult
              )
          ).flatten
        }

      case req @ DELETE -> Root / "user" / UUIDVar(userId) =>
        val resp = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          _                  <- userService.deleteUser(userId)
        } yield refreshResult

        resp.fold(
          err => ErrorMapper.toResponse(err),
          refreshResult =>
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> "User deleted successfully".asJson)),
              refreshResult
            )
        ).flatten

      case req @ DELETE -> Root / "user" =>
        val resp = for {
          (jwtClaims, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          userId = UUID.fromString(jwtClaims.getSubject)
          _ <- userService.deleteUser(userId)
        } yield ()

        resp.fold(
          err => ErrorMapper.toResponse(err),
          _ => Ok(Json.obj("message" -> "User deleted successfully".asJson))
        ).flatten

    }
}
