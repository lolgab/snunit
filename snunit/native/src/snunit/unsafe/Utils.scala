package snunit.unsafe

import java.nio.charset.Charset
import scala.scalanative.runtime.ByteArray
import scala.scalanative.unsafe._

private[snunit] object Utils {
  @extern
  private object libc {
    // to avoid unsigned number in Scala Native's libc
    def memcpy(dest: Ptr[Byte], src: Ptr[Byte], size: Long): Ptr[Byte] = extern
  }
  private val charset = Charset.defaultCharset()

  def fromCStringAndSize(cstr: CString, size: Int): String = {
    if (size > 0) {
      val bytes = new Array[Byte](size)

      libc.memcpy(bytes.asInstanceOf[ByteArray].at(0), cstr, size)

      new String(bytes, charset)
    } else ""
  }
}
