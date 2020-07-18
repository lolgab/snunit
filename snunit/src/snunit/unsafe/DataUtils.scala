package snunit.unsafe

import scala.scalanative.runtime._
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.unsafe.Ptr
import scala.scalanative.libc.stdlib
import scala.collection.mutable
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

private[snunit] object DataUtils {
  private val references = mutable.Set.empty[Long]

  @inline def getData[T <: Object](data: Ptr[nxt_unit_request_info_t]): T = {
    val rawptr = toRawPtr(data.unit.data)
    castRawPtrToObject(rawptr).asInstanceOf[T]
  }
  @inline def setData[T <: Object](data: Ptr[nxt_unit_init_t], obj: T): Unit = {
    val rawptr = castObjectToRawPtr(obj)
    val dataLong = castRawPtrToLong(rawptr)
    references += dataLong
    data.data = fromRawPtr(rawptr)
  }
}
