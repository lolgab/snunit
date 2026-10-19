package snunit.tests

import cats.effect.*
import sttp.tapir.*

object TapirOtel4s extends snunit.TapirOtel4s {
  def serverEndpoints =
    Resource.eval(otel4s.flatMap(_.tracerProvider.get("tapir-otel4s"))).map { tracer =>
      endpoint.get
        .in("hello")
        .in(query[String]("name"))
        .out(stringBody)
        .serverLogicSuccess[IO](name => tracer.span("greet").surround(IO.pure(s"Hello $name!"))) :: Nil
    }
}
