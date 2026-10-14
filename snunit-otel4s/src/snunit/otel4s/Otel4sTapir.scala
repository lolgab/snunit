package snunit.otel4s

import cats.effect.IO
import cats.effect.Resource
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.Otel4s
import org.typelevel.otel4s.sdk.OpenTelemetrySdk
import org.typelevel.otel4s.sdk.exporter.otlp.autoconfigure.OtlpExportersAutoConfigure
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.Tracer
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.RequestHandler
import sttp.tapir.server.interceptor.RequestInterceptor
import sttp.tapir.server.interceptor.Responder
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.model.ServerResponse

object Otel4sTapir {

  /** The OpenTelemetry SDK configured by the `OTEL_*` environment variables, which SNUnit sets from
    * [[snunit.config.Observability]], exporting over OTLP/HTTP.
    */
  def sdk: Resource[IO, Otel4s[IO]] =
    OpenTelemetrySdk
      .autoConfigured[IO](_.addExportersConfigurer(OtlpExportersAutoConfigure[IO]))
      .map(_.sdk)

  /** A server span for every request, a child of the span FreeUnit sends (continuing the trace of the `traceparent`
    * header).
    */
  def interceptor(tracer: Tracer[IO]): RequestInterceptor[IO] = new RequestInterceptor[IO] {
    def apply[R, B](
        responder: Responder[IO, B],
        requestHandler: EndpointInterceptor[IO] => RequestHandler[IO, R, B]
    ): RequestHandler[IO, R, B] =
      RequestHandler.from { (request, endpoints, monad) =>
        val headers = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
        val method = request.method.method
        tracer.joinOrRoot(headers) {
          tracer
            .spanBuilder(method)
            .withSpanKind(SpanKind.Server)
            .addAttributes(
              Attribute("http.request.method", method),
              Attribute("url.path", request.uri.pathSegments.segments.map(_.v).mkString("/", "/", ""))
            )
            .build
            .use { span =>
              requestHandler(EndpointInterceptor.noop[IO])(request, endpoints)(using monad).flatTap {
                (result: RequestResult[B]) =>
                  result match {
                    case RequestResult.Response(response: ServerResponse[B], _) =>
                      span.addAttribute(Attribute("http.response.status_code", response.code.code.toLong))
                    case _ => IO.unit
                  }
              }
            }
        }
      }
  }
}
