package snunit.unsafe

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio._
import java.nio.charset.Charset
import java.nio.charset.CoderResult
import scala.scalanative.memory.PointerBuffer
import scala.scalanative.unsafe._

private val charset = Charset.defaultCharset()

// CharsetEncoder/Decoder and ByteBuffer are stateful and not thread-safe:
// create them per call so concurrent requests (e.g. cats-effect worker threads) don't corrupt each other.
private final val bufferSize = 4000

private[snunit] def fromCStringAndSize(cstr: CString, size: Int): String = {
  if (size > 0) {
    val decoder = charset.newDecoder()
    val inputBuffer = PointerBuffer.wrap(cstr, size)

    val output: CharBuffer = CharBuffer.allocate(size)
    decoder.decode(inputBuffer, output, true)

    new String(output.array(), 0, output.position())
  } else ""
}

private[snunit] inline def readStringBytesWith(string: String)(inline f: ByteBuffer => Unit) = {
  val input: CharBuffer = CharBuffer.wrap(string)
  val encoder = charset.newEncoder()
  val buffer = ByteBuffer.allocate(bufferSize)

  var result = CoderResult.OVERFLOW
  while (result.isOverflow()) {
    buffer.clear()
    result = encoder.encode(input, buffer, true)
    buffer.flip()
    f(buffer)
  }
}

private[snunit] def stringBytes(string: String): ByteBuffer = {
  val input: CharBuffer = CharBuffer.wrap(string)

  charset.newEncoder().encode(input)
}

extension (buffer: ByteBuffer)
  inline def pointer: Ptr[Byte] = buffer.array.at(0)
  inline def contentLength: Int = buffer.limit()
