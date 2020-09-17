package snunit.unsafe

import java.nio.charset.Charset

import scala.scalanative.unsafe._

private[snunit] object Utils {
  def fromCStringAndSize(cstr: CString, size: Int, charset: Charset = Charset.defaultCharset()): String = {
    val bytes = new Array[Byte](size)

    var c = 0
    while (c < size) {
      bytes(c) = !(cstr + c)
      c += 1
    }

    new String(bytes, charset)
  }
}
