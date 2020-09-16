package snunit.unsafe

import scala.scalanative.runtime._
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.unsafe.Ptr
import scala.scalanative.libc.stdlib
import scala.collection.mutable
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

private[snunit] object PtrUtils {
  private val references = mutable.Set.empty[Long]

  @inline def fromPtr[T <: Object](data: Ptr[Byte]): T = {
    val rawptr = toRawPtr(data)
    castRawPtrToObject(rawptr).asInstanceOf[T]
  }
  @inline def toPtr[T <: Object](obj: T): Ptr[Byte] = {
    val rawptr = castObjectToRawPtr(obj)
    val dataLong = castRawPtrToLong(rawptr)
    references += dataLong
    fromRawPtr(rawptr)
  }
}
