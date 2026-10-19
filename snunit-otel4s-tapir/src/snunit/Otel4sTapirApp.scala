package snunit

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.ResourceIO
import org.typelevel.otel4s.Otel4s
import snunit.otel4s.Otel4sSdk
import snunit.otel4s.Otel4sTapir
import sttp.tapir.server.ServerEndpoint

/** A [[TapirApp]] traced with [[https://typelevel.org/otel4s otel4s]]: every request is a span, a child of the span
  * FreeUnit sends, and the `Otel4s` instance, configured by SNUnit's observability (`snunit run --stack` included), is
  * given to `serverEndpoints` to create your own spans and metrics.
  *
  * {{{
  * object Main extends snunit.Otel4sTapirApp {
  *   def serverEndpoints(otel4s: Otel4s[IO]) = Resource.eval(otel4s.tracerProvider.get("my-service")).map { tracer =>
  *     endpoint.get.in("hello").out(stringBody)
  *       .serverLogicSuccess[IO](_ => tracer.span("work").surround(IO.pure("hello"))) :: Nil
  *   }
  * }
  * }}}
  */
trait Otel4sTapirApp extends IOApp.Simple {
  def serverEndpoints(otel4s: Otel4s[IO]): Resource[IO, List[ServerEndpoint[Any, IO]]]

  /** Configures FreeUnit and the standalone executable. */
  def unitConfig: ResourceIO[snunit.config.UnitConfig] = Resource.pure(snunit.config.UnitConfig())

  /** The name of the instrumentation scope of the spans of the requests. */
  def tracerName: String = "snunit"

  override def run: IO[Unit] = (for {
    config <- unitConfig
    otel4s <- Otel4sSdk.resource
    tracer <- Resource.eval(otel4s.tracerProvider.get(tracerName))
    endpoints <- serverEndpoints(otel4s)
  } yield (config, endpoints, tracer)).use { case (config, endpoints, tracer) =>
    tapir.SNUnitServerBuilder
      .default[IO]
      .withConfig(config)
      .withInterceptors(Otel4sTapir.interceptor(tracer) :: Nil)
      .withServerEndpoints(endpoints)
      .run
  }
}
