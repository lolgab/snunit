package snunit.http4s

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.effect.syntax.all._
import cats.syntax.all._
import org.http4s
import org.http4s.HttpApp
import org.typelevel.ci.CIString
import org.typelevel.vault.Vault
import snunit.AsyncServer

import java.util.concurrent.CancellationException

private[http4s] object Impl {
  @inline
  private def toHttp4sRequest[F[_]](req: snunit.Request): http4s.Request[F] = {
    @inline
    def toHttp4sMethod(method: snunit.Method): http4s.Method =
      http4s.Method.fromString(method.name).getOrElse(throw new Exception(s"Method not valid ${method.name}"))
    @inline
    def toHttp4sUri(req: snunit.Request): http4s.Uri =
      http4s.Uri.fromString(req.target).getOrElse(throw new Exception(s"Uri not valid ${req.target}"))
    @inline
    def toHttp4sHeaders(req: snunit.Request): http4s.Headers = {
      val builder = List.newBuilder[http4s.Header.Raw]
      var i = 0
      while (i < req.headersLength) {
        builder += http4s.Header.Raw(CIString(req.headerNameUnsafe(i)), req.headerValueUnsafe(i))
        i += 1
      }
      http4s.Headers(builder.result())
    }
    @inline
    def toHttp4sVersion(req: snunit.Request): http4s.HttpVersion = {
      http4s.HttpVersion.fromString(req.version).getOrElse(throw new Exception(s"Version not valid ${req.version}"))
    }
    http4s.Request[F](
      toHttp4sMethod(req.method),
      toHttp4sUri(req),
      httpVersion = toHttp4sVersion(req),
      headers = toHttp4sHeaders(req),
      VersionSpecific.toHttp4sBody(req),
      attributes = Vault.empty
    )
  }
  def buildServer[F[_]: Async](
      dispatcher: Dispatcher[F],
      httpApp: HttpApp[F],
      errorHandler: Throwable => F[http4s.Response[F]]
  ): Resource[F, AsyncServer] = {
    Resource.eval(
      Async[F].delay(
        snunit.AsyncServerBuilder.build(new snunit.Handler {
          def handleRequest(req: snunit.Request): Unit = {
            val run = httpApp
              .run(toHttp4sRequest[F](req))
              .start
              .flatMap(_.joinWith(Async[F].raiseError(new CancellationException)))
              .handleErrorWith(errorHandler)
              .handleError(_ =>
                http4s.Response(http4s.Status.InternalServerError).putHeaders(http4s.headers.`Content-Length`.zero)
              )
              .flatMap { response =>
                val headers = response.headers.headers.map { h => (h.name.toString, h.value) }
                VersionSpecific.writeResponse(req, response, response.status.code, headers)
              }
            dispatcher.unsafeRunAndForget(run)
          }
        })
      )
    )
  }
}
