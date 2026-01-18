package utils.memory

import cats.effect.*
import cats.effect.kernel.Ref

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

object ConfirmUsersToken {

  private val store: Ref[IO, Map[String, (UUID, Instant)]] =
    Ref.unsafe[IO, Map[String, (UUID, Instant)]](Map.empty)

  def put(otpHash: String, userId: UUID): IO[Unit] =
    Clock[IO].realTimeInstant.flatMap { now =>
      store.update(_ + (otpHash -> (userId, now)))
    }

  def cleanExpired: IO[Unit] =
    for {
      now <- Clock[IO].realTimeInstant
      _ <- store.update { map =>
        map.filter {
          case (_, (_, ts)) =>
            ChronoUnit.MINUTES.between(ts, now) < 30
        }
      }
    } yield ()

  def remove(key: String): IO[Option[UUID]] =
    for {
      now <- Clock[IO].realTimeInstant
      result <- store.modify { map =>
        map.get(key) match {
          case Some((otpHash, ts)) if ChronoUnit.HOURS.between(ts, now) < 1 =>
            (map - key, Some(otpHash))
          case _ =>
            (map, None)
        }
      }
    } yield result

}
