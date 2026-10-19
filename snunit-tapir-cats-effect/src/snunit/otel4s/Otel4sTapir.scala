package snunit.otel4s

import cats.effect.IO
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.Tracer
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.RequestHandler
import sttp.tapir.server.interceptor.RequestInterceptor
import sttp.tapir.server.interceptor.Responder
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.model.ServerResponse

/** Tracing of a Tapir application with [[https://typelevel.org/otel4s otel4s]].
  *
  * {{{
  * object Main extends IOApp.Simple {
  *   def run = Otel4sSdk.resource.use { otel4s =>
  *     otel4s.tracerProvider.get("my-service").flatMap { tracer =>
  *       SNUnitServerBuilder
  *         .default[IO]
  *         .withInterceptors(Otel4sTapir.interceptor(tracer) :: Nil)
  *         .withServerEndpoints(endpoints(tracer))
  *         .run
  *     }
  *   }
  * }
  * }}}
  */
object Otel4sTapir {

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
