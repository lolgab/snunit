package snunit.http4s

import cats.effect.Async
import cats.syntax.all._
import org.http4s

private[http4s] object Utils {
  @inline
  def sendStreaming[F[_]: Async](
      req: snunit.Request,
      body: http4s.EntityBody[F],
      statusCode: Int,
      headers: Seq[(String, String)]
  ) = {
    req.startSend(statusCode, headers)
    body.chunks
      .map {
        case fs2.Chunk.ArraySlice(array, offset, length) =>
          req.sendBatch(array, offset, length)
        case chunk =>
          req.sendBatch(chunk.toArray)
      }
      .compile
      .drain >> Async[F].delay(req.sendDone())
  }
}
