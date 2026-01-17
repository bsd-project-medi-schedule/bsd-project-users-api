package service

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.either._
import java.util.UUID
import repo.UserRepo
import utils.BcryptService
import utils.ValidationService
import DTO.UserDTO
import error.ServiceError

final case class UserService(userRepo: UserRepo) {

  def createUser(userDTO: UserDTO): EitherT[IO, ServiceError, UUID] = {
    for {
      password <- EitherT.fromOption[IO](
        userDTO.password,
        ServiceError.BadRequest("Password is required"): ServiceError
      )
      _ <- EitherT.cond[IO](
        ValidationService.isValidEmail(userDTO.email),
        (),
        ServiceError.BadRequest("Invalid email format"): ServiceError
      )
      _ <- EitherT.cond[IO](
        ValidationService.isValidPassword(password),
        (),
        ServiceError.BadRequest("Password must be at least 8 characters"): ServiceError
      )
      emailTaken <- EitherT.liftF(userRepo.emailExists(userDTO.email))
      _ <- EitherT.cond[IO](
        !emailTaken,
        (),
        ServiceError.BadRequest("Email already registered"): ServiceError
      )
      hashedPassword = BcryptService.hashPassword(password)
      userToCreate = userDTO.copy(password = Some(hashedPassword))
      userId <- EitherT.fromOptionF(
        userRepo.createAndGetId(userToCreate),
        ServiceError.InternalError("Could not create user"): ServiceError
      )
    } yield userId
  }

  def readUser(userId: UUID): EitherT[IO, ServiceError, UserDTO] =
    EitherT.fromOptionF(userRepo.readUserById(userId), ServiceError.NotFound("Could not find user"): ServiceError)

  def readUsers(offset: Int, size: Int): EitherT[IO, ServiceError, List[UserDTO]] =
    EitherT(userRepo.readUsers(offset, size).map(users =>
      users.asRight[ServiceError]
    ))

  def readBySearch(
    input: String,
    offset: Int,
    size: Int
  ): EitherT[IO, ServiceError, List[UserDTO]] =
    EitherT(userRepo.readBySearch(input, offset, size).map(users => users.asRight[ServiceError]))

  def validateCredentials(email: String, password: String): EitherT[IO, ServiceError, UserDTO] = {
    for {
      _ <- EitherT.cond[IO](
        ValidationService.isValidEmail(email),
        (),
        ServiceError.BadRequest("Invalid email format"): ServiceError
      )
      user <- EitherT.fromOptionF(
        userRepo.validateUser(email),
        ServiceError.Forbidden("Invalid credentials"): ServiceError
      )
      storedPassword <- EitherT.fromOption[IO](
        user.password,
        ServiceError.InternalError("User has no password"): ServiceError
      )
      _ <- EitherT.cond[IO](
        BcryptService.verifyPassword(password, storedPassword),
        (),
        ServiceError.Forbidden("Invalid credentials"): ServiceError
      )
    } yield user.copy(password = None)
  }

  def updateUser(userId: UUID, userDTO: UserDTO): EitherT[IO, ServiceError, Unit] = {
    EitherT(userRepo.updateUser(userId, userDTO).map {
      case 0 => Left(ServiceError.NotFound("Could not find user"))
      case _ => Right(())
    })
  }

  def updatePassword(userId: UUID, newPassword: String): EitherT[IO, ServiceError, Unit] = {
    for {
      _ <- EitherT.cond[IO](
        ValidationService.isValidPassword(newPassword),
        (),
        ServiceError.BadRequest("Password must be at least 8 characters"): ServiceError
      )
      hashedPassword = BcryptService.hashPassword(newPassword)
      result <- EitherT(userRepo.updatePassword(userId, hashedPassword).map {
        case 0 => Left(ServiceError.NotFound("Could not find user"): ServiceError)
        case _ => Right(())
      })
    } yield result
  }

  def deleteUser(userId: UUID): EitherT[IO, ServiceError, Unit] =
    EitherT(userRepo.deleteById(userId).map {
      case 0 => Left(ServiceError.NotFound("Could not find user"))
      case _ => Right(())
    })

}