package snunit.unsafe

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio._
import java.nio.charset.Charset
import java.nio.charset.CoderResult
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

@extern
private object libc {
  // to avoid unsigned number in Scala Native's libc
  def memcpy(dest: Ptr[Byte], src: Ptr[Byte], size: Long): Ptr[Byte] = extern
}
private val charset = Charset.defaultCharset()
private val encoder = charset.newEncoder()
private val decoder = charset.newDecoder()

private final val sharedBufferSize = 4000
private final val sharedBuffer = ByteBuffer.allocate(sharedBufferSize)

private final val emptyCharArray = new Array[Char](0)

private[snunit] def fromCStringAndSize(cstr: CString, size: Int): String = {
  if (size > 0) {
    val inputBuffer =
      if (size < sharedBufferSize) {
        sharedBuffer.clear()
        sharedBuffer.limit(size)
        sharedBuffer
      } else
        ByteBuffer.allocate(size)

    libc.memcpy(inputBuffer.array().at(0), cstr, size)

    val output: CharBuffer = CharBuffer.allocate(size)
    decoder.reset()
    decoder.decode(inputBuffer, output, true)

    // write String fields
    val result = new String(emptyCharArray)
    val resultRawPtr = castObjectToRawPtr(result)
    val resultPtr = fromRawPtr[Byte](resultRawPtr)
    storeObject(toRawPtr(resultPtr + 8), castObjectToRawPtr(output.array()))
    storeInt(toRawPtr(resultPtr + 16), 0)
    val length = output.position()
    storeInt(toRawPtr(resultPtr + 20), length)
    result
  } else ""
}

private[snunit] inline def readStringBytesWith(string: String)(inline f: ByteBuffer => Unit) = {
  val input: CharBuffer = newCharBuffer(string)

  var result = CoderResult.OVERFLOW
  while (result.isOverflow()) {
    encoder.reset()
    sharedBuffer.clear()
    result = encoder.encode(input, sharedBuffer, true)
    sharedBuffer.flip()
    f(sharedBuffer)
  }
}

private[snunit] def stringBytes(string: String): ByteBuffer = {
  val input: CharBuffer = newCharBuffer(string)

  encoder.reset()
  encoder.encode(input)
}

extension (buffer: ByteBuffer)
  inline def pointer: Ptr[Byte] = buffer.array.at(0)
  inline def contentLength: Int = buffer.limit()
