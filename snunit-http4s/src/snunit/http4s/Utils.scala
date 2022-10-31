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
    Async[F].delay(req.startSend(statusCode, headers)) >>
      body.chunks
        .foreach {
          case fs2.Chunk.ArraySlice(array, offset, length) =>
            Async[F].delay(req.sendBatch(array, offset, length))
          case chunk =>
            Async[F].delay(req.sendBatch(chunk.toArray))
        }
        .compile
        .drain >> Async[F].delay(req.sendDone())
  }
}
