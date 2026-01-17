package impl

import cats.effect.IO
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.fragment.Fragment
import doobie.Transactor

import java.util.UUID
import repo.UserRepo
import DTO.UserDTO

final case class UserRepoImpl()(implicit t: Transactor[IO]) extends UserRepo {

  override def createAndGetId(user: UserDTO): IO[Option[UUID]] =
    sql"""
     INSERT INTO users (email, password, role, first_name, last_name, phone_number)
     VALUES (${user.email}, ${user.password}, ${user.role}, ${user.firstName}, ${user.lastName}, ${user.phoneNumber})
     RETURNING id
     """.query[UUID].option.transact(t)

  override def readUserById(id: UUID): IO[Option[UserDTO]] =
    sql"""
      SELECT id, email, NULL AS password, role, first_name, last_name, phone_number, created_at
      FROM users
      WHERE id = $id
      """.query[UserDTO].option.transact(t)

  override def validateUser(email: String): IO[Option[UserDTO]] =
    sql"""
      SELECT id, email, password, role, first_name, last_name, phone_number, created_at
      FROM users
      WHERE email = $email
      """.query[UserDTO].option.transact(t)

  override def readUserByEmail(email: String): IO[Option[UserDTO]] =
    sql"""
      SELECT id, email, NULL AS password, role, first_name, last_name, phone_number, created_at
      FROM users
      WHERE email = $email
      """.query[UserDTO].option.transact(t)

  override def readUsers(offset: Int, size: Int): IO[List[UserDTO]] =
    sql"""
    SELECT id, email, NULL AS password, role, first_name, last_name, phone_number, created_at
    FROM users
    ORDER BY last_name, first_name
    LIMIT $size OFFSET $offset
  """.query[UserDTO].to[List].transact(t)

  override def readBySearch(input: String, offset: Int, size: Int): IO[List[UserDTO]] = {
    val terms: List[String] =
      input.split("\\s+").toList.filter(_.nonEmpty)

    val whereClause: Fragment =
      terms.foldLeft(fr"TRUE") { (acc, term) =>
        val pat = s"%$term%"
        acc ++ fr" AND (first_name ILIKE $pat OR last_name ILIKE $pat OR email ILIKE $pat)"
      }

    val q: Fragment =
      fr"""
      SELECT id, email, NULL AS password, role, first_name, last_name, phone_number, created_at
      FROM users
      WHERE
    """ ++ whereClause ++
        fr"""
      ORDER BY last_name, first_name
      LIMIT $size OFFSET $offset
    """
    q.query[UserDTO].to[List].transact(t)
  }

  override def emailExists(email: String): IO[Boolean] =
    sql"SELECT EXISTS(SELECT 1 FROM users WHERE email = $email)"
      .query[Boolean].unique.transact(t)

  override def updateUser(userId: UUID, newUser: UserDTO): IO[Int] =
    sql"UPDATE users SET first_name = ${newUser.firstName}, last_name = ${newUser.lastName} WHERE id = $userId"
      .update.run.transact(t)

  override def updatePassword(userId: UUID, newPasswordHash: String): IO[Int] =
    sql"UPDATE users SET password = $newPasswordHash WHERE id = $userId"
      .update.run.transact(t)

  override def deleteById(id: UUID): IO[Int] =
    sql"""
     DELETE FROM users where id = $id
     """.update.run.transact(t)

}