package snunit.tests

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import snunit.http4s.*

object Http4sHelloWorld extends IOApp.Simple {
  def helloWorldRoutes: HttpRoutes[IO] = {
    HttpRoutes.of[IO] { case GET -> Root =>
      Ok("Hello Http4s!")
    }
  }

  def run: IO[Unit] = {
    SNUnitServerBuilder
      .default[IO]
      .withHttpApp(helloWorldRoutes.orNotFound)
      .run
  }
}
