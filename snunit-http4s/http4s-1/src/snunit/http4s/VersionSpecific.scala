package snunit.http4s

import cats.effect.Async
import org.http4s
import org.http4s.Entity
import scodec.bits.ByteVector
import snunit.*

object VersionSpecific {
  inline def toHttp4sBody[F[_]](req: snunit.Request): http4s.Entity[F] = {
    Entity.strict(ByteVector(req.contentRaw()))
  }

  inline def writeResponse[F[_]: Async](
      req: snunit.Request,
      response: http4s.Response[F],
      statusCode: Int,
      headers: snunit.Headers
  ): F[Unit] = {
    response.entity match {
      case Entity.Strict(bytes) =>
        Async[F].delay(req.send(StatusCode(statusCode), bytes.toArray, headers))
      case Entity.Streamed(body, _) =>
        Utils.sendStreaming(req, body, statusCode, headers)
      case Entity.Empty =>
        Async[F].delay(req.send(StatusCode(statusCode), Array.emptyByteArray, headers))
    }
  }
}
