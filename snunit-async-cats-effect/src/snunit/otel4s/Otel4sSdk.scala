package snunit.otel4s

import cats.effect.IO
import cats.effect.Resource
import org.typelevel.otel4s.Otel4s
import org.typelevel.otel4s.sdk.OpenTelemetrySdk
import org.typelevel.otel4s.sdk.exporter.otlp.autoconfigure.OtlpExportersAutoConfigure

object Otel4sSdk {

  /** The OpenTelemetry SDK configured by the `OTEL_*` environment variables, which SNUnit sets from
    * [[snunit.config.Observability]], exporting over OTLP/HTTP.
    */
  def resource: Resource[IO, Otel4s[IO]] =
    OpenTelemetrySdk
      .autoConfigured[IO](_.addExportersConfigurer(OtlpExportersAutoConfigure[IO]))
      .map(_.sdk)
}
