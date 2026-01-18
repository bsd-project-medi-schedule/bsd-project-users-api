package natstools.handlers

import cats.effect.IO
import nats.{EventHandler, NatsEvent}
import natstools.events.UserCreatedEvent

object UserCreatedHandler extends EventHandler {
  override val handles: String = "user.created"

  override def handle(event: NatsEvent): IO[List[NatsEvent]] = event match {
    case e: UserCreatedEvent =>
      IO.delay {
        List.empty
      }
    case _ => IO.pure(List.empty)
  }
}