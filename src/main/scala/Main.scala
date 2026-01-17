import cats.effect.*
import cats.implicits.toSemigroupKOps
import config.{AppConfig, ConfigUtils}
import config.objects.NetworkConfig
import db.DbContext
import doobie.Transactor
import factory.{AuthFactory, UserFactory}
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import utils.JwtService
import utils.memory.NewUsersTokens

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

object Main extends IOApp.Simple {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private def runPeriodicTask(name: String, task: IO[Unit], interval: FiniteDuration): IO[Unit] =
    Stream.awakeEvery[IO](interval)
      .evalMap(_ => task.handleErrorWith(e => logger.error(e)(s"TTL check failed for task $name")))
      .compile
      .drain

  private def startServer(cfg: AppConfig): Resource[IO, Unit] = {
    for {
      dbTransactor <- DbContext(cfg.dbConnectionConfig)

      implicit0(t: Transactor[IO]) = dbTransactor.transactor
      implicit0(networkConfig: NetworkConfig) = cfg.networkConfig
      implicit0(jwtService: JwtService) = JwtService(cfg.authConfig)

      uf <- UserFactory()
      af <- AuthFactory(uf.userService, uf.loginSessionService)

      allRoutes = Seq(
        uf.userRoutes,
        af.authRoutes,
      )

      routes = allRoutes.reduce(_ <+> _).orNotFound
      _ <- BlazeServerBuilder[IO]
        .bindHttp(cfg.networkConfig.appPort, cfg.networkConfig.appHost)
        .withHttpApp(routes)
        .resource

    } yield ()
  }

  override def run: IO[Unit] =
    for {
      _   <- logger.info("Starting main server")
      cfg <- ConfigUtils.loadAndParse[AppConfig]("application.conf", "application")
      _   <- logger.info("Config loaded")
      _   <- IO.println("Starting server")
      _ <- startServer(cfg).use { _ =>

        val ttlRegister = runPeriodicTask("Register tokens", NewUsersTokens.cleanExpired, 1.hour)

        List(
          IO.never,
          ttlRegister,
        ).parSequence_.void
      }
    } yield ()
}
