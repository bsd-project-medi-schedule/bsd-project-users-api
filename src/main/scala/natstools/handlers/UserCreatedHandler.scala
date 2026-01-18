package natstools.handlers

import cats.effect.IO
import nats.{EventHandler, NatsEvent}
import natstools.events.UserCreatedEvent

object UserCreatedHandler extends EventHandler {
  override val handles: String = "user.created"

  override def handle(event: NatsEvent): IO[List[NatsEvent]] = event match {
    case e: UserCreatedEvent =>
      IO.delay {
        println(s"[UserCreatedHandler] Processing user creation: ${e.userId}")
        List(
          NatsEvent.create[NatsEvent.UserWelcomeEmailSent]((id, ts) =>
            NatsEvent.UserWelcomeEmailSent(id, ts, e.userId, e.email)
          ),
          NatsEvent.create[NatsEvent.UserProfileInitialized]((id, ts) =>
            NatsEvent.UserProfileInitialized(id, ts, e.userId)
          )
        )
      }
    case _ => IO.pure(List.empty)
  }
}