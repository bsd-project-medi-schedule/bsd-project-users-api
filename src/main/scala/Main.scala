import cats.effect.*
import cats.implicits.toSemigroupKOps
import cats.syntax.all.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import config.objects.AuthConfig
import config.objects.NatsConfig
import config.objects.NetworkConfig
import config.AppConfig
import config.CORS.MainCorsPolicy
import config.ConfigUtils
import config.Logging
import config.middleware.ErrorHandlerMiddleware
import db.DbContext
import db.FlywayMigratorApp
import doobie.Transactor
import factory.AuthFactory
import factory.UserFactory
import fs2.Stream
import impl.LoginSessionRepoImpl
import impl.UserRepoImpl
import nats.EventBus
import nats.EventHandler
import nats.EventProcessor
import nats.NatsClient
import natstools.handlers.LoginSessionHandler
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.HttpRoutes
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import service.LoginSessionService
import service.UserService
import utils.memory.NewUsersTokens
import utils.JwtService

object Main extends IOApp.Simple with Logging {

  private def eventBusResource(cfg: NatsConfig): Resource[IO, EventBus] =
    NatsClient.resource(cfg).map(client => EventBus.fromNats(client, cfg))

  private def startEventProcessor(handlers: Seq[EventHandler])(implicit
    eventBus: EventBus
  ): Resource[IO, Unit] =
    for {
      processor <- Resource.eval(EventProcessor.create(eventBus))
      handlersIO = IO.parSequence(handlers.map(processor.register))
      _ <- Resource.eval(handlersIO)
      _ <- Resource.make(processor.run.compile.drain.start)(_.cancel).void
    } yield ()

  private def runPeriodicTask(name: String, task: IO[Unit], interval: FiniteDuration): IO[Unit] =
    Stream.awakeEvery[IO](interval)
      .evalMap(_ => task.handleErrorWith(e => logger.error(e)(s"TTL check failed for task $name")))
      .compile
      .drain

  private def startServer(routes: HttpRoutes[IO])(implicit
    networkConfig: NetworkConfig
  ): Resource[IO, Unit] = {
    val host = Host.fromString(networkConfig.appHost).getOrElse(Host.fromString("0.0.0.0").get)
    val port = Port.fromInt(networkConfig.appPort).getOrElse(Port.fromInt(7000).get)

    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(routes.orNotFound)
      .build
      .void
  }

  private def buildClient(): Resource[IO, Client[IO]] =
    EmberClientBuilder
      .default[IO]
      .build

  private def buildApp(cfg: AppConfig): Resource[IO, (Unit, Unit)] =
    (for {
      eventBus <- eventBusResource(cfg.natsConfig)
      implicit0(eb: EventBus) = eventBus

      dbTransactor <- DbContext(cfg.dbConnectionConfig)
      implicit0(xa: Transactor[IO]) = dbTransactor.transactor

      emberClient <- buildClient()
      implicit0(c: Client[IO]) = emberClient

      implicit0(authConfig: AuthConfig) = cfg.authConfig
      implicit0(networkConfig: NetworkConfig) = cfg.networkConfig

      userRepo = UserRepoImpl()
      loginSessionRepo = LoginSessionRepoImpl()

      userService = UserService(userRepo)
      loginSessionService: LoginSessionService = LoginSessionService(loginSessionRepo)
      jwtService = JwtService()

      userHttp <- UserFactory(userService, jwtService)
      authHttp <- AuthFactory(userService, jwtService, loginSessionService)

      allRoutes = Seq(
        userHttp.routes(),
        authHttp.routes(),
      ).reduce(_ <+> _)

      routesWithErrorHandling = ErrorHandlerMiddleware(allRoutes)
      mainCorsRoutes = MainCorsPolicy(routesWithErrorHandling)

      loginSessionHandler = LoginSessionHandler(loginSessionRepo)

      natsHandlers = Seq(
        loginSessionHandler
      )

    } yield (startServer(mainCorsRoutes), startEventProcessor(natsHandlers)).parTupled).flatten

  override def run: IO[Unit] =
    for {
      _ <- logger.info("=== Running the Migrations ===")
      _ <- FlywayMigratorApp.migrate()
      _ <- logger.info("Migrations completed")

      _   <- logger.info("Starting main server")
      cfg <- ConfigUtils.loadAndParse[AppConfig]("application.conf", "application")
      _   <- logger.info("Config loaded")
      _   <- logger.info("")

      _ <- logger.info("Starting server")
      _ <- logger.info("")

      _ <- logger.info("=== NATS JetStream Event System Starting ===")
      _ <-
        logger.info(s"Connected to: nats://${cfg.natsConfig.natsHost}:${cfg.natsConfig.natsPort}")
      _ <- logger.info(
        s"Stream '${cfg.natsConfig.streamName}' configured. Handlers registered. Starting event processing..."
      )
      _ <- logger.info("")

      _ <- buildApp(cfg).use { _ =>
        val ttlRegister = runPeriodicTask("Register tokens", NewUsersTokens.cleanExpired, 1.hour)

        List(
          IO.never,
          ttlRegister,
        ).parSequence_.void
      }
    } yield ()

}
