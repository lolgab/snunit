package snunit.unsafe

import java.nio.charset.Charset
import scala.scalanative.libc.string.memcpy
import scala.scalanative.runtime.ByteArray
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

private[snunit] object Utils {
  private val charset = Charset.defaultCharset()

  def fromCStringAndSize(cstr: CString, size: Int): String = {
    if (size > 0) {
      val bytes = new Array[Byte](size)

      memcpy(bytes.asInstanceOf[ByteArray].at(0), cstr, size.toULong)

      new String(bytes, charset)
    } else ""
  }
}
