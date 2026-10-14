package snunit.tests

import cats.effect.*
import org.typelevel.otel4s.Otel4s
import sttp.tapir.*

object TapirOtel4s extends snunit.Otel4sTapirApp {
  def serverEndpoints(otel4s: Otel4s[IO]) =
    Resource.eval(otel4s.tracerProvider.get("tapir-otel4s")).map { tracer =>
      endpoint.get
        .in("hello")
        .in(query[String]("name"))
        .out(stringBody)
        .serverLogicSuccess[IO](name => tracer.span("greet").surround(IO.pure(s"Hello $name!"))) :: Nil
    }
}
