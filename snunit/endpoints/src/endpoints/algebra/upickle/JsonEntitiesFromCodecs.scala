package endpoints.algebra.upickle

import endpoints.{Invalid, Valid, Validated}
import endpoints.algebra.Codec

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import upickle.default._

trait JsonEntitiesFromCodecs extends endpoints.algebra.JsonEntitiesFromCodecs {
  type JsonCodec[A] = ReadWriter[A]

  def stringCodec[A: ReadWriter]: Codec[String, A] = new Codec[String, A] {
    def decode(from: String): Validated[A] = {
      try {
        Valid(read[A](from))
      } catch {
        case NonFatal(e) =>
          Invalid(Seq(e.toString()))
      }
    }
    def encode(from: A): String = write(from)
  }
}
