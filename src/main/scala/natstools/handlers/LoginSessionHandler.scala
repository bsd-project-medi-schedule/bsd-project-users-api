package natstools.handlers

import cats.effect.IO
import nats.EventHandler
import nats.NatsEvent
import natstools.events.LoginSessionEvent
import repo.LoginSessionRepo

final class LoginSessionHandler(
  loginSessionRepo: LoginSessionRepo
) extends EventHandler {

  override val handles: String = LoginSessionEvent.EVENT_TYPE

  override def handle(event: NatsEvent): IO[List[NatsEvent]] =
    event match {
      case e: LoginSessionEvent =>
        for {
          _ <- loginSessionRepo.updateToken(e.oldToken, e.newToken)
        } yield List.empty

      case _ => IO.pure(List.empty)
    }
}

object LoginSessionHandler {
  def apply(loginSessionRepo: LoginSessionRepo): EventHandler =
    new LoginSessionHandler(loginSessionRepo)
}
