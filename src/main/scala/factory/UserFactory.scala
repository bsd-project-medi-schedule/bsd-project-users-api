package factory

import cats.effect.IO
import cats.effect.Resource
import config.objects.NetworkConfig
import config.CORS.MainCorsPolicy
import doobie.Transactor
import http.UserHttp
import impl.LoginSessionRepoImpl
import impl.UserRepoImpl
import org.http4s.HttpRoutes
import service.LoginSessionService
import service.UserService
import utils.JwtService

final case class UserFactory(
  userService: UserService,
  loginSessionService: LoginSessionService,
  userRoutes: HttpRoutes[IO],
)

object UserFactory {

  def apply()(implicit
    t: Transactor[IO],
    jwtService: JwtService,
    networkConfig: NetworkConfig
  ): Resource[IO, UserFactory] =
    Resource.eval(IO {
      val userRepo = UserRepoImpl()
      val userService = UserService(userRepo)

      val loginSessionRepo = LoginSessionRepoImpl()
      val loginSessionService = LoginSessionService(loginSessionRepo)

      val userHttp = UserHttp(userService, loginSessionService)
      val userCors = MainCorsPolicy(userHttp.routes())

      UserFactory(userService, loginSessionService, userCors)
    })

}
