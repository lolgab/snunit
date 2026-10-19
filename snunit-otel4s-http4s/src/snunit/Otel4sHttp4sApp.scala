package snunit

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.ResourceIO
import org.http4s.HttpApp
import org.typelevel.otel4s.Otel4s
import snunit.http4s.SNUnitServerBuilder
import snunit.otel4s.Otel4sHttp4s
import snunit.otel4s.Otel4sSdk

/** An [[Http4sApp]] traced with [[https://typelevel.org/otel4s otel4s]]: every request is a span, a child of the span
  * FreeUnit sends, and the `Otel4s` instance, configured by SNUnit's observability (`snunit run --stack` included), is
  * given to `routes` to create your own spans and metrics.
  *
  * {{{
  * object Main extends snunit.Otel4sHttp4sApp {
  *   def routes(otel4s: Otel4s[IO]) = Resource.eval(otel4s.tracerProvider.get("my-service")).map { tracer =>
  *     HttpRoutes.of[IO] { case GET -> Root / "hello" => tracer.span("work").surround(Ok("hello")) }.orNotFound
  *   }
  * }
  * }}}
  */
trait Otel4sHttp4sApp extends IOApp.Simple {
  def routes(otel4s: Otel4s[IO]): Resource[IO, HttpApp[IO]]

  /** Configures FreeUnit and the standalone executable. */
  def unitConfig: ResourceIO[snunit.config.UnitConfig] = Resource.pure(snunit.config.UnitConfig())

  /** The name of the instrumentation scope of the spans of the requests. */
  def tracerName: String = "snunit"

  override def run: IO[Unit] = (for {
    config <- unitConfig
    otel4s <- Otel4sSdk.resource
    tracer <- Resource.eval(otel4s.tracerProvider.get(tracerName))
    app <- routes(otel4s)
  } yield (config, Otel4sHttp4s.middleware(tracer)(app))).use { case (config, app) =>
    SNUnitServerBuilder
      .default[IO]
      .withConfig(config)
      .withHttpApp(app)
      .run
  }
}
