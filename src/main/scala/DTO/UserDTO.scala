package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import java.time.Instant
import java.util.UUID

case class UserDTO(
  id: Option[UUID],
  email: String,
  password: Option[String],
  role: Option[Int],
  firstName: String,
  lastName: String,
  phoneNumber: String,
  createdAt: Option[Instant]
)

object UserDTO {
  implicit val codec: Codec[UserDTO] = deriveCodec
}