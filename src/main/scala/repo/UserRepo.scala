package repo

import DTO.UserDTO
import cats.effect.IO

import java.util.UUID

trait UserRepo {
  def createAndGetId(user: UserDTO): IO[Option[UUID]]

  def readUserById(id: UUID): IO[Option[UserDTO]]

  def validateUser(email: String): IO[Option[UserDTO]];

  def readUserByEmail(email: String): IO[Option[UserDTO]]

  def readUsers(offset: Int, size: Int): IO[List[UserDTO]]

  def readBySearch(input: String, offset: Int, size: Int): IO[List[UserDTO]]

  def emailExists(email: String): IO[Boolean]

  def updateUser(userId: UUID, newUser: UserDTO): IO[Int]

  def updatePassword(userId: UUID, newPasswordHash: String): IO[Int]

  def deleteById(id: UUID): IO[Int]
}