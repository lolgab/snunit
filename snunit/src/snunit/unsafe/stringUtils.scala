package snunit.unsafe

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio._
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CharsetEncoder
import java.nio.charset.CoderResult
import scala.scalanative.memory.PointerBuffer
import scala.scalanative.runtime.GC
import scala.scalanative.runtime.Intrinsics.castObjectToRawPtr
import scala.scalanative.runtime.Intrinsics.castRawPtrToObject
import scala.scalanative.runtime.Intrinsics.loadInt
import scala.scalanative.runtime.Intrinsics.loadObject
import scala.scalanative.runtime.Intrinsics.storeBoolean
import scala.scalanative.runtime.Intrinsics.storeInt
import scala.scalanative.runtime.Intrinsics.storeObject
import scala.scalanative.runtime.fromRawPtr
import scala.scalanative.runtime.toRawPtr
import scala.scalanative.unsafe._

private val charset = Charset.defaultCharset()

/* CharsetEncoder/Decoder are not thread-safe. Handlers can run on worker threads. */
private val encoder = new ThreadLocal[CharsetEncoder] {
  override def initialValue(): CharsetEncoder = charset.newEncoder()
}
private val decoder = new ThreadLocal[CharsetDecoder] {
  override def initialValue(): CharsetDecoder = charset.newDecoder()
}

private final val sharedBufferSize = 4000
private val sharedBuffer = new ThreadLocal[ByteBuffer] {
  override def initialValue(): ByteBuffer = ByteBuffer.allocate(sharedBufferSize)
}

private[snunit] def fromCStringAndSize(cstr: CString, size: Int): String = {
  if (size > 0) {
    val inputBuffer = PointerBuffer.wrap(cstr, size)

    val output: CharBuffer = CharBuffer.allocate(size)
    val dec = decoder.get()
    dec.reset()
    dec.decode(inputBuffer, output, true)

    // write String fields
    val result = new String(Array.emptyCharArray)
    val resultRawPtr = castObjectToRawPtr(result)
    val resultPtr = fromRawPtr[Byte](resultRawPtr)
    storeObject(toRawPtr(resultPtr + StringCharArrayOffset), castObjectToRawPtr(output.array()))
    storeInt(toRawPtr(resultPtr + StringOffsetOffset), 0)
    val length = output.position()
    storeInt(toRawPtr(resultPtr + StringCountOffset), length)
    result
  } else ""
}

private[snunit] inline def readStringBytesWith(string: String)(inline f: ByteBuffer => Unit) = {
  val input: CharBuffer = newCharBuffer(string)
  val enc = encoder.get()
  val buf = sharedBuffer.get()

  var result = CoderResult.OVERFLOW
  while (result.isOverflow()) {
    enc.reset()
    buf.clear()
    result = enc.encode(input, buf, true)
    buf.flip()
    f(buf)
  }
}

private[snunit] def stringBytes(string: String): ByteBuffer = {
  val input: CharBuffer = newCharBuffer(string)
  val enc = encoder.get()
  enc.reset()
  enc.encode(input)
}

extension (buffer: ByteBuffer)
  inline def pointer: Ptr[Byte] = buffer.array.at(0)
  inline def contentLength: Int = buffer.limit()
