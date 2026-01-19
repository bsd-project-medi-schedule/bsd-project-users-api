package factory

import cats.effect.IO
import cats.effect.Resource
import config.objects.NetworkConfig
import http.AuthHttp
import nats.EventBus
import org.http4s.client.Client
import service.LoginSessionService
import service.UserService
import utils.JwtService

object AuthFactory {
  def apply(
    userService: UserService,
    jwtService: JwtService,
    loginSessionService: LoginSessionService,
  )(implicit
    client: Client[IO],
    eventBus: EventBus,
    networkConfig: NetworkConfig
  ): Resource[IO, AuthHttp] =
    Resource.eval(IO(AuthHttp()(userService, jwtService, loginSessionService, client, eventBus, networkConfig)))
}
