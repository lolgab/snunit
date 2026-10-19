package snunit.tests

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.typelevel.otel4s.Otel4s

object Http4sOtel4s extends snunit.Otel4sHttp4sApp {
  def routes(otel4s: Otel4s[IO]) =
    Resource.eval(otel4s.tracerProvider.get("http4s-otel4s")).map { tracer =>
      HttpRoutes
        .of[IO] { case GET -> Root / "hello" :? NameParam(name) =>
          tracer.span("greet").surround(Ok(s"Hello $name!"))
        }
        .orNotFound
    }

  object NameParam extends QueryParamDecoderMatcher[String]("name")
}
