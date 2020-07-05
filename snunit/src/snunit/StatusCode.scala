package snunit

class StatusCode(val value: Int)

object StatusCode {
  case object OK extends StatusCode(200)
  case object Created extends StatusCode(201)
  case object Accepted extends StatusCode(202)
  case object NoContent extends StatusCode(204)
  case object BadRequest extends StatusCode(400)
  case object Unauthorized extends StatusCode(401)
  case object Forbidden extends StatusCode(403)
  case object NotFound extends StatusCode(404)
  case object PayloadTooLarge extends StatusCode(413)
  case object TooManyRequests extends StatusCode(429)
  case object InternalServerError extends StatusCode(500)
  case object NotImplemented extends StatusCode(501)
}
