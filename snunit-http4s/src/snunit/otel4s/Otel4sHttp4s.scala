package snunit.otel4s

import cats.data.Kleisli
import cats.effect.IO
import org.http4s.HttpApp
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.Tracer

object Otel4sHttp4s {

  /** A server span for every request, a child of the span FreeUnit sends (continuing the trace of the `traceparent`
    * header).
    */
  def middleware(tracer: Tracer[IO]): HttpApp[IO] => HttpApp[IO] = app =>
    Kleisli { request =>
      val headers = request.headers.headers.map(h => h.name.toString.toLowerCase -> h.value).toMap
      val method = request.method.name
      tracer.joinOrRoot(headers) {
        tracer
          .spanBuilder(method)
          .withSpanKind(SpanKind.Server)
          .addAttributes(
            Attribute("http.request.method", method),
            Attribute("url.path", request.uri.path.renderString)
          )
          .build
          .use { span =>
            app.run(request).flatTap { response =>
              span.addAttribute(Attribute("http.response.status_code", response.status.code.toLong))
            }
          }
      }
    }
}
