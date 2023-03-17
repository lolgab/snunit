package snunit.http4s

import cats.effect.Async
import org.http4s
import org.http4s.Entity
import org.http4s.Entity.Default
import org.http4s.Entity.Empty
import org.http4s.Entity.Strict
import scodec.bits.ByteVector
import snunit._

object VersionSpecific {
  inline def toHttp4sBody[F[_]](req: snunit.Request): http4s.Entity[F] = {
    Entity.strict(ByteVector(req.contentRaw()))
  }

  inline def writeResponse[F[_]: Async](
      req: snunit.Request,
      response: http4s.Response[F],
      statusCode: Int,
      headers: Seq[(String, String)]
  ): F[Unit] = {
    response.entity match {
      case Default(body, _) =>
        Utils.sendStreaming(req, body, statusCode, headers)
      case Empty =>
        Async[F].delay(req.send(StatusCode(statusCode), Array.emptyByteArray, headers))
      case Strict(bytes) =>
        Async[F].delay(req.send(StatusCode(statusCode), bytes.toArray, headers))
    }
  }
}
