package natstools.events

import io.circe.*
import io.circe.generic.semiauto.*
import java.time.Instant
import java.util.UUID
import nats.NatsEvent

case class UserDeleteEvent(
  eventId: UUID,
  timestamp: Instant,
  userId: UUID,
) extends NatsEvent {
  val eventType: String = UserDeleteEvent.EVENT_TYPE
  protected def baseEncoder: Encoder[this.type] =
    UserDeleteEvent.codec.asInstanceOf[Encoder[this.type]]
}

object UserDeleteEvent {
  private val EVENT_TYPE = "user.deleted"
  implicit val codec: Codec[UserDeleteEvent] = deriveCodec
  NatsEvent.register(EVENT_TYPE, codec)
}
