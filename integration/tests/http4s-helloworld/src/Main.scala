package snunit.tests

import cats.effect._
import epollcat.EpollApp
import org.http4s._
import org.http4s.dsl.io._
import snunit.http4s._

object Http4sHelloWorld extends EpollApp.Simple {
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
