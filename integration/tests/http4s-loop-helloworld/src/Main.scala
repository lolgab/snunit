package snunit.tests

import cats.effect._
import org.http4s._
import org.http4s.dsl._
import snunit.http4s._

object Http4sHelloWorld extends LoopIOApp {
  def helloWorldRoutes: HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._
    HttpRoutes.of[IO] { case GET -> Root =>
      Ok("Hello Http4s Loop!")
    }
  }

  def run(args: List[String]): IO[ExitCode] = {
    SNUnitServerBuilder
      .default[IO]
      .withHttpApp(helloWorldRoutes.orNotFound)
      .build
      .useForever
      .as(ExitCode.Success)
  }
}
