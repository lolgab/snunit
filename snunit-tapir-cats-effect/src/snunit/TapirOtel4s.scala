package snunit

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Resource
import org.typelevel.otel4s.Otel4s
import snunit.otel4s.Otel4sSdk
import snunit.tapir.Otel4sTapir
import sttp.tapir.server.interceptor.Interceptor

/** A [[TapirApp]] traced with [[https://typelevel.org/otel4s otel4s]]: every request is a span, a child of the span
  * FreeUnit sends. The SDK is configured by SNUnit's observability (`snunit run --stack` included) and [[otel4s]] gives
  * it to `serverEndpoints` to create your own spans and metrics.
  *
  * Extend this instead of [[TapirApp]] to enable it. The otel4s code is only linked into the applications that do, so
  * the other ones don't pay for it (nor need `s2n`, see the README).
  *
  * {{{
  * object Main extends snunit.TapirOtel4s {
  *   def serverEndpoints = Resource.eval(otel4s.flatMap(_.tracerProvider.get("my-service"))).map { tracer =>
  *     endpoint.get.in("hello").out(stringBody)
  *       .serverLogicSuccess[IO](_ => tracer.span("work").surround(IO.pure("hello"))) :: Nil
  *   }
  * }
  * }}}
  */
trait TapirOtel4s extends TapirApp {

  /** The name of the instrumentation scope of the spans of the requests. */
  def tracerName: String = "snunit"

  private val sdk = Deferred.unsafe[IO, Otel4s[IO]]

  /** The `Otel4s` of the application, available when `serverEndpoints` is acquired. */
  final def otel4s: IO[Otel4s[IO]] = sdk.get

  override def serverInterceptors: Resource[IO, List[Interceptor[IO]]] = for {
    otel <- Otel4sSdk.resource
    _ <- Resource.eval(sdk.complete(otel))
    tracer <- Resource.eval(otel.tracerProvider.get(tracerName))
  } yield Otel4sTapir.interceptor(tracer) :: Nil
}
