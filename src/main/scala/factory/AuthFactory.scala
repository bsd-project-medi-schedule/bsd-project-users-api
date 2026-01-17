package factory

import cats.effect.IO
import cats.effect.Resource
import config.objects.NetworkConfig
import config.CORS.MainCorsPolicy
import http.AuthHttp
import org.http4s.HttpRoutes
import service.LoginSessionService
import service.UserService
import utils.JwtService

final case class AuthFactory(
  authRoutes: HttpRoutes[IO]
)

object AuthFactory {
  def apply(userService: UserService)(implicit
    jwtService: JwtService,
    loginSessionService: LoginSessionService,
    networkConfig: NetworkConfig
  ): Resource[IO, AuthFactory] =
    Resource.eval(IO {
      val authHttp = AuthHttp(userService)
      val authCors = MainCorsPolicy(authHttp.routes())

      AuthFactory(authCors)
    })
}
