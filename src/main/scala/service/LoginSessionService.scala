package service

import cats.data.EitherT
import cats.effect.IO
import java.util.UUID
import repo.LoginSessionRepo
import DTO.LoginSessionDTO
import error.ServiceError

final case class LoginSessionService(loginSessionRepo: LoginSessionRepo) {

  def createAndGetId(loginSession: LoginSessionDTO): EitherT[IO, ServiceError, UUID] =
    EitherT.fromOptionF(
      loginSessionRepo.createAndGetId(loginSession),
      ServiceError.InternalError("Could not create token")
    )

  def readByToken(token: String): EitherT[IO, ServiceError, LoginSessionDTO] =
    EitherT.fromOptionF(
      loginSessionRepo.readByToken(token),
      ServiceError.Forbidden("No valid token found")
    )

  def updateToken(oldToken: String, newToken: String): EitherT[IO, ServiceError, Unit] =
    EitherT(loginSessionRepo.updateToken(oldToken, newToken).map {
      case 0 => Left(ServiceError.NotFound("Session not found"))
      case _ => Right(())
    })

  def deleteByToken(token: String): EitherT[IO, ServiceError, Unit] =
    EitherT(loginSessionRepo.deleteByToken(token).map {
      case 0 => Left(ServiceError.NotFound("Session not found"))
      case _ => Right(())
    })

  def cleanExpired(): IO[Int] =
    loginSessionRepo.cleanExpired()

}