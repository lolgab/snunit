package endpoints.snunit.server

import endpoints.algebra

import scala.concurrent.Future

/**
  * Interpreter for the [[algebra.ChunkedEntities]] algebra in the [[endpoints.akkahttp.server]] family.
  *
  * @group interpreters
  */
trait ChunkedEntities
    extends algebra.ChunkedEntities
    with EndpointsWithCustomErrors {

  type Chunks[A] = Null

  def textChunksRequest: RequestEntity[Chunks[String]] =
    ???

  def textChunksResponse: ResponseEntity[Chunks[String]] =
    ???

  def bytesChunksRequest: RequestEntity[Chunks[Array[Byte]]] =
    ???
  def bytesChunksResponse: ResponseEntity[Chunks[Array[Byte]]] =
    ???

}

/**
  * Interpreter for the [[algebra.ChunkedJsonEntities]] algebra in the [[endpoints.akkahttp.server]] family.
  *
  * @group interpreters
  */
trait ChunkedJsonEntities
    extends algebra.ChunkedJsonEntities
    with ChunkedEntities
    with JsonEntitiesFromCodecs {

  def jsonChunksRequest[A](
      implicit codec: JsonCodec[A]
  ): RequestEntity[Chunks[A]] = {
    ???
  }

  def jsonChunksResponse[A](
      implicit codec: JsonCodec[A]
  ): ResponseEntity[Chunks[A]] = {
    ???
  }

}
