package snunit.http4s

import cats.effect.Async
import org.http4s

private[http4s] object VersionSpecific {
  @inline
  def toHttp4sBody[F[_]](req: snunit.Request): http4s.EntityBody[F] = {
    fs2.Stream.chunk(fs2.Chunk.array(req.contentRaw))
  }

  @inline
  def writeResponse[F[_]: Async](
      req: snunit.Request,
      response: http4s.Response[F],
      statusCode: snunit.StatusCode,
      headers: Seq[(String, String)]
  ): F[Unit] = {
    Utils.sendStreaming(
      req,
      response.body,
      statusCode,
      headers
    )
  }

}
