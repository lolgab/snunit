package snunit.tests

import snunit.http4s._
import org.http4s.dsl._
import org.http4s._
import cats.syntax.all._
import cats.effect._

object Http4sHelloWorld extends IOApp {
  def helloWorldRoutes: HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._
    HttpRoutes.of[IO] { case GET -> Root =>
      Ok("Hello Http4s!")
    }
  }

  def run(args: List[String]): IO[ExitCode] = {
    SNUnitServerBuilder.default
      .withHttpApp(helloWorldRoutes.orNotFound)
      .build
      .map(_ => ExitCode.Success)
  }
}
