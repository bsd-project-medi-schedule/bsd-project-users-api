package http

import cats.data.EitherT
import cats.effect.IO
import io.circe.syntax.*
import io.circe.Json
import java.time.Duration
import java.time.Instant
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.dsl.io.*
import org.http4s.HttpRoutes
import service.LoginSessionService
import service.UserService
import utils.JwtService
import utils.NonceService
import utils.Sha256Service
import config.objects.NetworkConfig
import DTO.LoginDTO
import DTO.LoginSessionDTO
import error.ServiceError

final case class AuthHttp(
  usersService: UserService,
)(implicit jwtService: JwtService, loginSessionService: LoginSessionService, networkConfig: NetworkConfig) {

  private object RoleMatcher extends QueryParamDecoderMatcher[Int]("role")

  def routes(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case req @ GET -> Root / "auth" :? RoleMatcher(role) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, role)
        } yield refreshResult

        result.fold(
          err => ErrorMapper.toResponse(err),
          refreshResult =>
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> "Authentication passed".asJson)),
              refreshResult
            )
        ).flatten

      case req @ POST -> Root / "auth" =>
        req.as[LoginDTO].flatMap { loginData =>
          val result = for {
            userData <- usersService.validateCredentials(loginData.email, loginData.password)
            userId <- EitherT.fromOption[IO](
              userData.id,
              ServiceError.InternalError("User has no ID"): ServiceError
            )
            jwt = jwtService.createToken(userId, userData.role.getOrElse(2), userData.firstName)

            expiresAt = Instant.now().plus(Duration.ofDays(30))
            refresh = NonceService.generateNonce()
            session = Sha256Service.hash(refresh)
            _ <- loginSessionService.createAndGetId(LoginSessionDTO(userId, session, Some(expiresAt)))
          } yield (refresh, jwt, loginData.rememberMe)

          result.fold(
            err => ErrorMapper.toResponse(err),
            res => {
              val (refresh, jwt, rememberMe) = res

              HttpUtils.attachRefreshToReq(
                HttpUtils.attachJwtToReq(
                  Ok(Json.obj("message" -> "User authenticated successfully".asJson)),
                  jwt
                ),
                refresh,
                rememberMe
              )
            }
          ).flatten
        }

      case req @ DELETE -> Root / "auth" =>
        val result = for {
          refresh <- EitherT.fromOption[IO](
            req.cookies.find(_.name == "refresh-token"),
            ServiceError.Forbidden("Refresh token cookie is missing"): ServiceError
          )
          refreshTokenHash = Sha256Service.hash(refresh.content)
          _ <- loginSessionService.deleteByToken(refreshTokenHash)
        } yield ()

        result.fold(
          err => ErrorMapper.toResponse(err),
          _ => HttpUtils.clearAuthCookies(Ok(Json.obj("message" -> "Logged out successfully".asJson)))
        ).flatten

    }
}