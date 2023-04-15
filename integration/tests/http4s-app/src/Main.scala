package snunit.tests

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

object Main extends snunit.Http4sApp {
  def routes = Resource.pure(
    HttpRoutes
      .of[IO] { case GET -> Root =>
        Ok("Hello Http4s App!")
      }
      .orNotFound
  )
}
