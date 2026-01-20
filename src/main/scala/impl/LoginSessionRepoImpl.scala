package impl

import cats.effect.IO
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.Transactor

import java.util.UUID
import DTO.LoginSessionDTO
import repo.LoginSessionRepo

final case class LoginSessionRepoImpl()(implicit t: Transactor[IO]) extends LoginSessionRepo{

  override def createAndGetId(loginSession: LoginSessionDTO): IO[Option[UUID]] =
    sql"""
     INSERT INTO login_sessions (id, token, expires_at)
     VALUES (${loginSession.id}, ${loginSession.token}, ${loginSession.expiresAt})
     ON CONFLICT (id) DO UPDATE SET
       token = EXCLUDED.token,
       expires_at = EXCLUDED.expires_at
     RETURNING id
  """.query[UUID].option.transact(t)

  override def readByToken(token: String): IO[Option[LoginSessionDTO]] =
    sql"""
    WITH target AS (
      SELECT id, token, expires_at
      FROM login_sessions
      WHERE token = $token
    ),
    expired AS (
      DELETE FROM login_sessions
      WHERE token = $token AND EXISTS (
        SELECT 1 FROM target WHERE expires_at <= NOW()
      )
      RETURNING id
    )
    SELECT id, token, expires_at
    FROM target
    WHERE expires_at > NOW()
  """.query[LoginSessionDTO].option
      .transact(t)

  override def updateToken(oldToken: String, newToken: String): IO[Int] =
    sql"""
      UPDATE login_sessions
      SET token = $newToken
      WHERE token = $oldToken
      """.update.run.transact(t)

  override def deleteByToken(token: String): IO[Int] =
    sql"""
      DELETE FROM login_sessions
      WHERE token = $token
      """.update.run.transact(t)

  override def cleanExpired(): IO[Int] =
    sql"""
      DELETE FROM login_sessions
      WHERE expires_at <= NOW();
      """.update.run.transact(t)
}
