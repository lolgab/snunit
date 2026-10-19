package snunit.tests

import cats.effect.*
import org.typelevel.otel4s.trace.Tracer
import snunit.otel4s.Otel4sSdk
import snunit.otel4s.Otel4sTapir
import snunit.tapir.*
import sttp.tapir.*

object TapirOtel4s extends IOApp.Simple {
  def helloWorld(tracer: Tracer[IO]) = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogicSuccess[IO](name => tracer.span("greet").surround(IO.pure(s"Hello $name!")))

  def run = Otel4sSdk.resource.use { otel4s =>
    otel4s.tracerProvider.get("tapir-otel4s").flatMap { tracer =>
      SNUnitServerBuilder
        .default[IO]
        .withInterceptors(Otel4sTapir.interceptor(tracer) :: Nil)
        .withServerEndpoints(helloWorld(tracer) :: Nil)
        .run
    }
  }
}
