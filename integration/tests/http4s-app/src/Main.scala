package snunit.tests

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import cats.syntax.all.{*, given}

object Main extends snunit.Http4sApp:
  final def routes = Resource.eval(IO(println("starting server"))) *> Resource.eval(
    IO(
      HttpRoutes
        .of[IO] { case req =>
          IO(println((s"Responding to $req"))) *> Ok("Hello Http4s App!")
        }
        .orNotFound
    )
  )
