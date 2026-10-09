package snunit.tapir

import sttp.capabilities.Streams

/** Streams capability of the synchronous interpreter. It only supports websockets, with pipes which are invoked for
  * each incoming message and return the messages to send back (in order). Pipes run on the server event loop, so they
  * must not block.
  */
trait SNUnitStreams extends Streams[SNUnitStreams] {
  override type BinaryStream = Nothing
  override type Pipe[A, B] = A => Iterable[B]
}

object SNUnitStreams extends SNUnitStreams
