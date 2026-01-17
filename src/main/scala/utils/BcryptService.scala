package utils

import org.mindrot.jbcrypt.BCrypt

object BcryptService {

  private val LOG_ROUNDS = 12

  def hashPassword(password: String): String =
    BCrypt.hashpw(password, BCrypt.gensalt(LOG_ROUNDS))

  def verifyPassword(password: String, hashedPassword: String): Boolean =
    BCrypt.checkpw(password, hashedPassword)

}