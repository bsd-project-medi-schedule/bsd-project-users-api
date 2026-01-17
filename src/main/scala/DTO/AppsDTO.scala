package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import java.util.UUID

case class AppsDTO(
  id: Option[UUID],
  label: String,
  url: String,
  iconUrl: String,
)

object AppsDTO {
  implicit val codec: Codec[AppsDTO] = deriveCodec
}
