package endpoints.snunit.server

// import endpoints.algebra.{Codec, Decoder, Encoder}
// import endpoints.{Invalid, Valid, Validated, algebra}
import endpoints.algebra

// /**
//   * Interpreter for [[algebra.JsonEntities]]
//   *
//   * To use it mix in support for your favourite Json library
//   * You can use one of [[https://github.com/hseeberger/akka-http-json hseeberger/akka-http-json]] modules
//   *
//   * @group interpreters
//   */
// trait JsonEntities extends algebra.JsonEntities with EndpointsWithCustomErrors {

//   type JsonRequest[A] = Null

//   def jsonRequest[A: JsonRequest]: RequestEntity[A] =
//     ???

//   type JsonResponse[A] = Null

//   def jsonResponse[A: JsonResponse]: ResponseEntity[A] =
//     ???

// }

/**
  * Interpreter for [[algebra.JsonEntitiesFromCodecs]] that decodes JSON requests and
  * encodes JSON responses using Akka HTTP.
  *
  * @group interpreters
  */
trait JsonEntitiesFromCodecs
    extends algebra.JsonEntitiesFromCodecs
    /* with EndpointsWithCustomErrors */ {

  def jsonRequest[A](implicit codec: JsonCodec[A]): RequestEntity[A] = ???
  def jsonResponse[A](implicit codec: JsonCodec[A]): ResponseEntity[A] = ???

}

// /**
//   * Interpreter for [[algebra.JsonEntitiesFromSchemas]] that decodes JSON requests and
//   * encodes JSON responses using Akka HTTP.
//   *
//   * @group interpreters
//   */
// trait JsonEntitiesFromSchemas
//     extends algebra.JsonEntitiesFromSchemas
//     with JsonEntitiesFromCodecs {

//   def stringCodec[A](implicit codec: JsonCodec[A]): Codec[String, A] =
//     codec.stringCodec

// }

// /**
//   * Interpreter for [[endpoints.algebra.JsonEntities]] that decodes JSON entities with a
//   * [[endpoints.algebra.Decoder]] and encodes JSON entities with an [[endpoints.algebra.Encoder]].
//   *
//   * The difference with [[JsonEntitiesFromCodecs]] is that you don’t need bidirectional codecs:
//   * you only need an encoder to build responses, or a decoder to decode requests.
//   *
//   * It is especially useful to encode `OpenApi` documents into JSON entities.
//   *
//   * @group interpreters
//   */
// trait JsonEntitiesFromEncodersAndDecoders
//     extends algebra.JsonEntities
//     with EndpointsWithCustomErrors {

//   type JsonRequest[A] = Decoder[String, A]
//   type JsonResponse[A] = Encoder[A, String]

//   def jsonRequest[A](implicit decoder: Decoder[String, A]): RequestEntity[A] =
//     JsonEntities.decodeJsonRequest(this)(decoder)

//   def jsonResponse[A](implicit encoder: Encoder[A, String]): ResponseEntity[A] =
//     JsonEntities.encodeJsonResponse(encoder)

// }

// private object JsonEntities {

//   def decodeJsonRequest[A](
//       endpoints: EndpointsWithCustomErrors
//   )(decoder: Decoder[String, A]) = {
//     ???
//   }

//   def encodeJsonResponse[A](
//       encoder: Encoder[A, String]
//   ): JsonResponse[A] =
//     ???

// }
