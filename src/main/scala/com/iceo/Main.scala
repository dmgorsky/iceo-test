package com.iceo

import cats.effect._
import cats.effect.unsafe.IORuntime.global
import com.iceo.config.Config
import com.iceo.db.Database
import com.iceo.repository.MessageRepository
import doobie.util.ExecutionContexts
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.server.http4s.Http4sServerInterpreter
import doobie.hikari.HikariTransactor
import sttp.tapir.AnyEndpoint
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.io.StdIn

object Main extends IOApp with Endpoints[IO] {

  override def run(args: List[String]): IO[ExitCode] = {

    val docs: List[AnyEndpoint] = List(getMessagesEndpoint)
    val swagger = SwaggerInterpreter().fromEndpoints[IO](docs, "iceo-test", "1.0.0")
    val swaggerRoute = Http4sServerInterpreter[IO]().toRoutes(swagger)

    resources().use { res =>
      for {
        _ <- Database.initialize(res.transactor)
        repository = new MessageRepository(res.transactor)
        logic = new EndpointsLogics[IO](repository)
        routes = Http4sServerInterpreter[IO]().toRoutes(
          List(
            getMessagesEndpoint.serverLogic(logic.getMessagesLogic)
          )
        )
        exitCode: ExitCode <- BlazeServerBuilder[IO]
          .bindHttp(res.config.server.port, res.config.server.host)
          .withHttpApp(Router("/" -> swaggerRoute, "/" -> routes).orNotFound)
          .resource
          .use { _ =>
            IO.blocking {
              println(s"Go to http://localhost:${res.config.server.port}/docs to open SwaggerUI. Press Enter to exit.")
              StdIn.readLine()
            }
          }
          .as(ExitCode.Success)
      } yield exitCode
    }

  }

  case class Resources(transactor: HikariTransactor[IO], config: Config)

  private def resources(configFile: String = "application.conf"): Resource[IO, Resources] = {
    for {
      config <- Config.load(configFile)
      ec <- ExecutionContexts.fixedThreadPool[IO](config.database.threadPoolSize)
      transactor <- Database.transactor(config.database, ec)
    } yield Resources(transactor, config)
  }

}
