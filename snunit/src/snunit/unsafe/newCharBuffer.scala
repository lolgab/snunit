package snunit.unsafe

import java.nio.CharBuffer
import scala.scalanative.runtime.{fromRawPtr, toRawPtr}
import scala.scalanative.runtime.Intrinsics.{castObjectToRawPtr, loadObject, loadInt}

private[snunit] def newCharBuffer(string: String): CharBuffer = {
  // deconstructing string
  val stringRawPtr = castObjectToRawPtr(string)
  val stringPtr = fromRawPtr[Byte](stringRawPtr)
  val array: Array[Char] = loadObject(toRawPtr(stringPtr + 8)).asInstanceOf[Array[Char]]
  val offset: Int = loadInt(toRawPtr(stringPtr + 16))
  val count: Int = loadInt(toRawPtr(stringPtr + 20))

  CharBuffer.wrap(array, offset, count)
}
