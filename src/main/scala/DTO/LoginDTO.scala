package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec

case class LoginDTO(
  email: String,
  password: String,
  rememberMe: Boolean
)

object LoginDTO {
  implicit val codec: Codec[LoginDTO] = deriveCodec
}