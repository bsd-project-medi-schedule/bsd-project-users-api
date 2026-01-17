package http

import cats.data.EitherT
import cats.effect.IO
import com.nimbusds.jwt.JWTClaimsSet
import config.objects.NetworkConfig
import error.ServiceError
import org.http4s.Response
import org.http4s.ResponseCookie
import org.http4s.SameSite
import service.LoginSessionService
import utils.JwtService
import utils.NonceService
import utils.Sha256Service

object HttpUtils {

  case class TokenRefreshResult(newJwt: Option[String], newRefresh: Option[String])

  def verifyTokenFromCookie(
    cookies: List[org.http4s.RequestCookie],
    minRoleNeeded: Int,
  )(implicit
    jwtService: JwtService,
    loginSessionService: LoginSessionService
  ): EitherT[IO, ServiceError, (JWTClaimsSet, TokenRefreshResult)] =
    for {
      refresh <- EitherT.fromOption[IO](
        cookies.find(_.name == "refresh-token"),
        ServiceError.Forbidden("Refresh token cookie is missing")
      )
      refreshTokenHash = Sha256Service.hash(refresh.content)
      loginSession <- loginSessionService.readByToken(refreshTokenHash)
      jwt <- EitherT.fromOption[IO](
        cookies.find(_.name == "auth-token"),
        ServiceError.Forbidden("Access token cookie is missing")
      )
      (jwtClaims, isExpired) <- EitherT.fromOption[IO](
        jwtService.verifyToken(jwt.content),
        ServiceError.Forbidden("Invalid access token")
      )
      userId = jwtClaims.getSubject
      _ <- EitherT.cond[IO](
        userId == loginSession.id.toString,
        (),
        ServiceError.Forbidden("Incompatible jwt - refresh")
      )
      role = jwtClaims.getStringClaim("role").toIntOption.getOrElse(2)
      _ <- EitherT.cond[IO](
        minRoleNeeded <= role,
        (),
        ServiceError.Forbidden("Incompatible jwt - refresh")
      )
      refreshResult <- if (isExpired) {
        val newJwt = jwtService.createToken(loginSession.id, role, jwtClaims.getStringClaim("firstName"))
        val newRefresh = NonceService.generateNonce()
        val newRefreshHash = Sha256Service.hash(newRefresh)
        loginSessionService.updateToken(refreshTokenHash, newRefreshHash)
          .map(_ => TokenRefreshResult(Some(newJwt), Some(newRefresh)))
      } else {
        EitherT.rightT[IO, ServiceError](TokenRefreshResult(None, None))
      }
    } yield (jwtClaims, refreshResult)

  def handleTokenRefresh(req: IO[Response[IO]], refreshResult: TokenRefreshResult)(implicit
    networkConfig: NetworkConfig
  ): IO[Response[IO]] =
    (refreshResult.newJwt, refreshResult.newRefresh) match {
      case (Some(jwt), Some(refresh)) =>
        attachRefreshToReq(attachJwtToReq(req, jwt), refresh, rememberMe = true)
      case (Some(jwt), None) =>
        attachJwtToReq(req, jwt)
      case _ => req
    }

  def attachJwtToReq(req: IO[Response[IO]], jwt: String)(implicit
    networkConfig: NetworkConfig
  ): IO[Response[IO]] = {
    val cookieExpiry = 15 * 60

    val jwtCookie = ResponseCookie(
      name = "auth-token",
      content = jwt,
      maxAge = Some(cookieExpiry),
      httpOnly = true,
      secure = networkConfig.secureCookies,
      sameSite = Some(SameSite.Strict),
      path = Some("/")
    )
    req.map(_.addCookie(jwtCookie))
  }

  def attachRefreshToReq(req: IO[Response[IO]], refresh: String, rememberMe: Boolean)(implicit
    networkConfig: NetworkConfig
  ): IO[Response[IO]] = {
    val refreshCookie = if (rememberMe) {
      val cookieExpiry = 30 * 24 * 60 * 60

      ResponseCookie(
        name = "refresh-token",
        content = refresh,
        maxAge = Some(cookieExpiry),
        httpOnly = true,
        secure = networkConfig.secureCookies,
        sameSite = Some(SameSite.Strict),
        path = Some("/")
      )
    } else {
      ResponseCookie(
        name = "refresh-token",
        content = refresh,
        httpOnly = true,
        secure = networkConfig.secureCookies,
        sameSite = Some(SameSite.Strict),
        path = Some("/")
      )
    }

    req.map(_.addCookie(refreshCookie))
  }

  def clearAuthCookies(req: IO[Response[IO]])(implicit
    networkConfig: NetworkConfig
  ): IO[Response[IO]] = {
    val clearedJwtCookie = ResponseCookie(
      name = "auth-token",
      content = "",
      maxAge = Some(0),
      httpOnly = true,
      secure = networkConfig.secureCookies,
      sameSite = Some(SameSite.Strict),
      path = Some("/")
    )
    val clearedRefreshCookie = ResponseCookie(
      name = "refresh-token",
      content = "",
      maxAge = Some(0),
      httpOnly = true,
      secure = networkConfig.secureCookies,
      sameSite = Some(SameSite.Strict),
      path = Some("/")
    )
    req.map(_.addCookie(clearedJwtCookie).addCookie(clearedRefreshCookie))
  }

}
