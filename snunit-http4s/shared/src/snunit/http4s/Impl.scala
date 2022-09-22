package snunit.http4s

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all._
import fs2.Chunk
import org.http4s
import org.http4s.HttpApp
import org.typelevel.ci.CIString
import org.typelevel.vault.Vault
import snunit.AsyncServer

private[http4s] class Impl[F[_]: Async] {
  @inline
  private def toHttp4sRequest(req: snunit.Request): http4s.Request[F] = {
    @inline
    def toHttp4sMethod(method: snunit.Method): http4s.Method =
      http4s.Method.fromString(method.name).getOrElse(throw new Exception(s"Method not valid ${method.name}"))
    @inline
    def toHttp4sUri(req: snunit.Request): http4s.Uri = http4s.Uri()
    @inline
    def toHttp4sHeaders(req: snunit.Request): http4s.Headers = {
      val builder = List.newBuilder[http4s.Header.Raw]
      for (i <- 0 until req.headersLength) {
        builder += http4s.Header.Raw(CIString(req.headerNameUnsafe(i)), req.headerValueUnsafe(i))
      }
      http4s.Headers(builder.result())
    }
    @inline
    def toHttp4sVersion(req: snunit.Request): http4s.HttpVersion = {
      http4s.HttpVersion.`HTTP/1.1`
    }
    @inline
    def toHttp4sBody(req: snunit.Request): http4s.EntityBody[F] = {
      fs2.Stream.chunk(Chunk.array(req.contentRaw))
    }
    @inline
    def toHttp4sAttributes(req: snunit.Request): Vault = {
      Vault.empty
    }

    http4s.Request[F](
      toHttp4sMethod(req.method),
      toHttp4sUri(req),
      httpVersion = toHttp4sVersion(req),
      headers = toHttp4sHeaders(req),
      body = toHttp4sBody(req),
      attributes = toHttp4sAttributes(req)
    )
  }
  def buildServer(
      dispatcher: Dispatcher[F],
      httpApp: HttpApp[F],
      errorHandler: Throwable => F[http4s.Response[F]]
  ): Resource[F, AsyncServer] = {
    Resource.eval(
      Async[F].delay(
        snunit.AsyncServerBuilder.build(new snunit.Handler {
          def handleRequest(req: snunit.Request): Unit = {
            val run = httpApp
              .run(toHttp4sRequest(req))
              .handleErrorWith(errorHandler)
              .handleError(_ =>
                http4s.Response(http4s.Status.InternalServerError).putHeaders(http4s.headers.`Content-Length`.zero)
              )
              .flatMap { response =>
                response.body.chunkAll
                  .map(chunk =>
                    req.send(
                      new snunit.StatusCode(response.status.code),
                      chunk.toArray,
                      response.headers.headers.map { h => (h.name.toString, h.value) }
                    )
                  )
                  .compile
                  .drain
              }
            dispatcher.unsafeRunAndForget(run)
          }
        })
      )
    )
  }
}
