import cats.effect.*
import cats.implicits.toSemigroupKOps
import config.objects.NetworkConfig
import config.AppConfig
import config.ConfigUtils
import db.DbContext
import doobie.Transactor
import factory.AuthFactory
import factory.UserFactory
import fs2.Stream
import nats.EventBus
import nats.EventProcessor
import nats.NatsClient
import natstools.handlers.UserCreatedHandler
import org.http4s.blaze.server.BlazeServerBuilder
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import service.LoginSessionService
import utils.memory.NewUsersTokens
import utils.JwtService

object Main extends IOApp.Simple {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private def runPeriodicTask(name: String, task: IO[Unit], interval: FiniteDuration): IO[Unit] =
    Stream.awakeEvery[IO](interval)
      .evalMap(_ => task.handleErrorWith(e => logger.error(e)(s"TTL check failed for task $name")))
      .compile
      .drain

  private def startServer(cfg: AppConfig)(implicit eventBus: EventBus): Resource[IO, Unit] =
    for {
      dbTransactor <- DbContext(cfg.dbConnectionConfig)

      implicit0(t: Transactor[IO])          = dbTransactor.transactor
      implicit0(networkConfig: NetworkConfig) = cfg.networkConfig
      implicit0(jwtService: JwtService)     = JwtService(cfg.authConfig)

      uf <- UserFactory()
      implicit0(loginSessionService: LoginSessionService) = uf.loginSessionService

      // Pass eventBus into AuthFactory
      af <- AuthFactory(uf.userService)

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

  private def eventBusResource(cfg: AppConfig): Resource[IO, EventBus] =
    for {
      client <- NatsClient.resource(cfg.natsConfig)
    } yield EventBus.fromNats(client, cfg.natsConfig)

  private def startEventProcessor(eventBus: EventBus): Resource[IO, Unit] =
    for {
      processor <- Resource.eval(EventProcessor.create(eventBus))
      _         <- Resource.eval(registerHandlers(processor))
      _         <- Resource.make(processor.run.compile.drain.start)(_.cancel)
    } yield ()

  private def registerHandlers(processor: EventProcessor): IO[Unit] =
    for {
      _ <- processor.register(UserCreatedHandler)
    } yield ()

  override def run: IO[Unit] =
    for {
      _   <- logger.info("Starting main server")
      cfg <- ConfigUtils.loadAndParse[AppConfig]("application.conf", "application")
      _   <- logger.info("Config loaded")
      _   <- IO.println("")

      _ <- IO.println("Starting server")
      _ <- IO.println("")

      _ <- IO.println("=== NATS JetStream Event System Starting ===")
      _ <- IO.println(s"Connected to: nats://${cfg.natsConfig.natsHost}:${cfg.natsConfig.natsPort}")
      _ <- IO.println(s"Stream '${cfg.natsConfig.streamName}' configured. Handlers registered. Starting event processing...")
      _ <- IO.println("")

      _ <- eventBusResource(cfg).use { implicit eventBus =>
        (startServer(cfg), startEventProcessor(eventBus)).parTupled.use { _ =>
          val ttlRegister = runPeriodicTask("Register tokens", NewUsersTokens.cleanExpired, 1.hour)

          List(
            IO.never,
            ttlRegister,
          ).parSequence_.void
        }
      }
    } yield ()

}
