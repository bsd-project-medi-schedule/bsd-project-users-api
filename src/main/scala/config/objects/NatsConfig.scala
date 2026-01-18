package config.objects

import config.ConfigCompanionBase
import pureconfig.generic.semiauto.deriveConvert
import pureconfig.ConfigConvert

final case class NatsConfig(
  natsHost: String,
  natsPort: Int,
  connectionName: String,
  streamName: String,
  streamSubjects: List[String]
)

object NatsConfig extends ConfigCompanionBase[NatsConfig] {
  implicit override val configConvert: ConfigConvert[NatsConfig] = deriveConvert[NatsConfig]
}
