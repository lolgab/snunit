package snunit.unsafe

import java.nio.CharBuffer
import scala.scalanative.runtime.Intrinsics.castObjectToRawPtr
import scala.scalanative.runtime.Intrinsics.loadInt
import scala.scalanative.runtime.Intrinsics.loadObject
import scala.scalanative.runtime.fromRawPtr
import scala.scalanative.runtime.toRawPtr

private[snunit] def newCharBuffer(string: String): CharBuffer = {
  // deconstructing string
  val stringRawPtr = castObjectToRawPtr(string)
  val stringPtr = fromRawPtr[Byte](stringRawPtr)
  val array: Array[Char] = loadObject(toRawPtr(stringPtr + StringCharArrayOffset)).asInstanceOf[Array[Char]]
  val offset: Int = loadInt(toRawPtr(stringPtr + StringOffsetOffset))
  val count: Int = loadInt(toRawPtr(stringPtr + StringCountOffset))

  CharBuffer.wrap(array, offset, count)
}
