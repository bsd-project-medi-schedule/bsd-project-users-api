package factory

import cats.effect.IO
import cats.effect.Resource
import config.objects.NetworkConfig
import http.UserHttp
import nats.EventBus
import org.http4s.client.Client
import service.UserService
import utils.JwtService

object UserFactory {

  def apply(
    userService: UserService,
    jwtService: JwtService,
  )(implicit
    client: Client[IO],
    eventBus: EventBus,
    networkConfig: NetworkConfig
  ): Resource[IO, UserHttp] =
    Resource.eval(IO(UserHttp()(userService, jwtService, client, eventBus, networkConfig)))

}
