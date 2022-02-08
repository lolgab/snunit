package snunit

import snunit.unsafe.Utils._

import scala.scalanative.unsafe._

object MethodUtils {
  def of(name: CString, nameSize: Byte): Method = {
    def default: Method = new Method(fromCStringAndSize(name, nameSize))
    if (nameSize == 3.toByte) {
      if (
        name(0) == 'G'.toByte &&
        name(1) == 'E'.toByte &&
        name(2) == 'T'.toByte
      ) Method.GET
      else if (
        name(0) == 'P'.toByte &&
        name(1) == 'U'.toByte &&
        name(2) == 'T'.toByte
      ) Method.PUT
      else default
    } else if (nameSize == 4.toByte) {
      if (
        name(0) == 'P'.toByte &&
        name(1) == 'O'.toByte &&
        name(2) == 'S'.toByte &&
        name(3) == 'T'.toByte
      ) Method.POST
      else if (
        name(0) == 'H'.toByte &&
        name(1) == 'E'.toByte &&
        name(2) == 'A'.toByte &&
        name(3) == 'D'.toByte
      ) Method.HEAD
      else default
    } else if (nameSize == 5.toByte) {
      if (
        name(0) == 'P'.toByte &&
        name(1) == 'A'.toByte &&
        name(2) == 'T'.toByte &&
        name(3) == 'C'.toByte &&
        name(4) == 'H'.toByte
      ) Method.PATCH
      else if (
        name(0) == 'T'.toByte &&
        name(1) == 'R'.toByte &&
        name(2) == 'A'.toByte &&
        name(3) == 'C'.toByte &&
        name(4) == 'E'.toByte
      ) Method.TRACE
      else default
    } else if (nameSize == 6.toByte) {
      if (
        name(0) == 'D'.toByte &&
        name(1) == 'E'.toByte &&
        name(2) == 'L'.toByte &&
        name(3) == 'E'.toByte &&
        name(4) == 'T'.toByte &&
        name(5) == 'E'.toByte
      ) Method.DELETE
      else default
    } else if (nameSize == 7.toByte) {
      if (
        name(0) == 'D'.toByte &&
        name(1) == 'E'.toByte &&
        name(2) == 'L'.toByte &&
        name(3) == 'E'.toByte &&
        name(4) == 'T'.toByte &&
        name(5) == 'E'.toByte
      ) Method.DELETE
      else default
    } else if (nameSize == 7.toByte) {
      if (
        name(0) == 'C'.toByte &&
        name(1) == 'O'.toByte &&
        name(2) == 'N'.toByte &&
        name(3) == 'N'.toByte &&
        name(4) == 'E'.toByte &&
        name(5) == 'C'.toByte &&
        name(6) == 'T'.toByte
      ) Method.CONNECT
      else default
    } else if (nameSize == 7.toByte) {
      if (
        name(0) == 'O'.toByte &&
        name(1) == 'P'.toByte &&
        name(2) == 'T'.toByte &&
        name(3) == 'I'.toByte &&
        name(4) == 'O'.toByte &&
        name(5) == 'N'.toByte &&
        name(6) == 'S'.toByte
      ) Method.OPTIONS
      else default
    } else default
  }
}
