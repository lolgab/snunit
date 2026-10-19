package snunit.tests

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*

object Http4sOtel4s extends snunit.Http4sOtel4s {
  def routes =
    Resource.eval(otel4s.flatMap(_.tracerProvider.get("http4s-otel4s"))).map { tracer =>
      HttpRoutes
        .of[IO] { case GET -> Root / "hello" :? NameParam(name) =>
          tracer.span("greet").surround(Ok(s"Hello $name!"))
        }
        .orNotFound
    }

  object NameParam extends QueryParamDecoderMatcher[String]("name")
}
