package snunit.http4s

import cats.effect.*
import cats.effect.std.Dispatcher
import cats.effect.syntax.all._
import cats.syntax.all._
import org.http4s
import org.http4s.HttpApp
import org.typelevel.ci.CIString
import org.typelevel.vault.Vault
import snunit.*

import java.util.concurrent.CancellationException

private[http4s] object Impl {
  def buildServer[F[_]: Async](
      httpApp: HttpApp[F],
      errorHandler: Throwable => F[http4s.Response[F]]
  ): F[Unit] = {
    for
      shutdownDeferred <- Deferred[F, F[Unit]]
      shutdown <- Dispatcher
        .parallel[F](await = true)
        .use { dispatcher =>
          Async[F].delay(
            snunit.AsyncServerBuilder
              .setRequestHandler(new snunit.RequestHandler {
                def handleRequest(req: snunit.Request): Unit = {
                  val run = httpApp
                    .run {
                      val method = req.method match {
                        case snunit.Method.GET     => http4s.Method.GET
                        case snunit.Method.HEAD    => http4s.Method.HEAD
                        case snunit.Method.POST    => http4s.Method.POST
                        case snunit.Method.PUT     => http4s.Method.PUT
                        case snunit.Method.DELETE  => http4s.Method.DELETE
                        case snunit.Method.CONNECT => http4s.Method.CONNECT
                        case snunit.Method.OPTIONS => http4s.Method.OPTIONS
                        case snunit.Method.TRACE   => http4s.Method.TRACE
                        case snunit.Method.PATCH   => http4s.Method.PATCH
                        case v =>
                          http4s.Method
                            .fromString(v)
                            .getOrElse(throw new Exception(s"Method not valid $v"))
                      }
                      val uri = {
                        val v = req.target
                        http4s.Uri.fromString(v).getOrElse(throw new Exception(s"Uri not valid $v"))
                      }
                      val headers = {
                        var list: List[http4s.Header.Raw] = Nil
                        var i = req.headersLength - 1
                        while (i >= 0) {
                          list = http4s.Header.Raw(CIString(req.headerNameUnsafe(i)), req.headerValueUnsafe(i)) :: list
                          i -= 1
                        }
                        http4s.Headers(list)
                      }
                      val version = req.version match {
                        case snunit.Version.`HTTP/1.1` => http4s.HttpVersion.`HTTP/1.1`
                        case snunit.Version.`HTTP/1.0` => http4s.HttpVersion.`HTTP/1.0`
                        case v =>
                          http4s.HttpVersion
                            .fromString(v)
                            .getOrElse(throw new Exception(s"Version not valid $v"))
                      }

                      http4s.Request[F](
                        method,
                        uri,
                        httpVersion = version,
                        headers = headers,
                        VersionSpecific.toHttp4sBody(req),
                        attributes = Vault.empty
                      )
                    }
                    .start
                    .flatMap(_.joinWith(Async[F].raiseError(new CancellationException)))
                    .handleErrorWith(errorHandler)
                    .handleError(_ =>
                      http4s
                        .Response(http4s.Status.InternalServerError)
                        .putHeaders(http4s.headers.`Content-Length`.zero)
                    )
                    .flatMap { response =>
                      val headers = Headers(response.headers.headers, _.name.toString, _.value)
                      VersionSpecific.writeResponse(req, response, response.status.code, headers)
                    }
                  dispatcher.unsafeRunAndForget(run)
                }
              })
              .setShutdownHandler(shutdown =>
                dispatcher.unsafeRunAndForget(shutdownDeferred.complete(Async[F].delay(shutdown())))
              )
              .build()
          ) *> shutdownDeferred.get
        }
      _ <- shutdown
    yield ()
  }
}
