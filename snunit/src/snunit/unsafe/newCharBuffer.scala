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
  val array: Array[Char] = loadObject(toRawPtr(stringPtr + 8)).asInstanceOf[Array[Char]]
  val offset: Int = loadInt(toRawPtr(stringPtr + 16))
  val count: Int = loadInt(toRawPtr(stringPtr + 20))

  CharBuffer.wrap(array, offset, count)
}
