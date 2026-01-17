package utils.memory

import cats.effect.*
import cats.effect.kernel.Ref

import java.time.Instant
import java.time.temporal.ChronoUnit

object NewUsersTokens {

  private val store: Ref[IO, Map[String, Instant]] =
    Ref.unsafe[IO, Map[String, Instant]](Map.empty)

  def put(otpHash: String): IO[Unit] =
    Clock[IO].realTimeInstant.flatMap { now =>
      store.update(_ + (otpHash -> now))
    }

  def cleanExpired: IO[Unit] =
    for {
      now <- Clock[IO].realTimeInstant
      _ <- store.update { map =>
        map.filter {
          case (_, ts) =>
            ChronoUnit.HOURS.between(ts, now) < 1
        }
      }
    } yield ()

  def remove(key: String): IO[Option[Unit]] =
    for {
      now <- Clock[IO].realTimeInstant
      result <- store.modify { map =>
        map.get(key) match {
          case Some(ts) if ChronoUnit.HOURS.between(ts, now) < 1 =>
            (map - key, Some(()))
          case _ =>
            (map, None)
        }
      }
    } yield result
}
