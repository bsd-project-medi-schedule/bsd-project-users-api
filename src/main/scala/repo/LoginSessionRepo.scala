package repo

import DTO.LoginSessionDTO
import cats.effect.IO

import java.util.UUID

trait LoginSessionRepo {
  def createAndGetId(loginSession: LoginSessionDTO): IO[Option[UUID]]

  def readByToken(token: String): IO[Option[LoginSessionDTO]]

  def updateToken(oldToken: String, newToken: String): IO[Int]

  def deleteByToken(token: String): IO[Int]

  def cleanExpired(): IO[Int]
}
