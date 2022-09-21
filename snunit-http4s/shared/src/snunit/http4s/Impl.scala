package snunit.http4s

import org.http4s.HttpApp
import snunit.AsyncServer
import org.typelevel.vault.Vault
import org.http4s
import fs2.Chunk
import cats.effect.IO

private[http4s] object Impl {
  @inline
  private def toHttp4sRequest(req: snunit.Request): http4s.Request[IO] = {
    @inline
    def toHttp4sMethod(method: snunit.Method): http4s.Method =
      http4s.Method.fromString(method.name).getOrElse(throw new Exception(s"Method not valid ${method.name}"))
    @inline
    def toHttp4sUri(req: snunit.Request): http4s.Uri = http4s.Uri()
    @inline
    def toHttp4sHeaders(req: snunit.Request): http4s.Headers = {
      http4s.Headers()
    }
    @inline
    def toHttp4sVersion(req: snunit.Request): http4s.HttpVersion = {
      http4s.HttpVersion.`HTTP/1.1`
    }
    @inline
    def toHttp4sBody(req: snunit.Request): http4s.EntityBody[IO] = {
      fs2.Stream.chunk(Chunk.array(req.contentRaw))
    }
    @inline
    def toHttp4sAttributes(req: snunit.Request): Vault = {
      Vault.empty
    }

    http4s.Request[IO](
      toHttp4sMethod(req.method),
      toHttp4sUri(req),
      httpVersion = toHttp4sVersion(req),
      headers = toHttp4sHeaders(req),
      body = toHttp4sBody(req),
      attributes = toHttp4sAttributes(req)
    )
  }
  def buildServer(httpApp: HttpApp[IO], errorHandler: Throwable => IO[http4s.Response[IO]]): IO[AsyncServer] = {
    IO(snunit.AsyncServerBuilder.build(new snunit.Handler {
      def handleRequest(req: snunit.Request): Unit = {
        val http4sRequest = toHttp4sRequest(req)
        httpApp
          .run(http4sRequest)
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
          .handleErrorWith(errorHandler)
          .handleError(_ =>
            http4s.Response(http4s.Status.InternalServerError).putHeaders(http4s.headers.`Content-Length`.zero)
          )
          .unsafeRunAndForget()(cats.effect.unsafe.implicits.global)
      }
    }))
  }
}
