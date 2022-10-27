package snunit

import scala.scalanative.annotation.alwaysinline
import scala.scalanative.runtime._
import scala.scalanative.unsafe._

private[snunit] object RunnableUtils {
  @alwaysinline def toPtr(runnable: Runnable): Ptr[Byte] =
    fromRawPtr(Intrinsics.castObjectToRawPtr(runnable))

  @alwaysinline def fromPtr(ptr: Ptr[Byte]): Runnable =
    Intrinsics.castRawPtrToObject(toRawPtr(ptr)).asInstanceOf[Runnable]
}
