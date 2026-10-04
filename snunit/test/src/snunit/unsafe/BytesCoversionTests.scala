package snunit.unsafe

import utest._
import scalanative.unsafe._

object foo {
  def bar() = {
    val s = "hello"
    val buffer = stringBytes(s)
    buffer.get() ==> 'h'
    buffer.get() ==> 'e'
    buffer.get() ==> 'l'
    buffer.get() ==> 'l'
    buffer.get() ==> 'o'
    assertThrows[java.nio.BufferUnderflowException] { buffer.get() }
  }
}

object BytesConversionTests extends TestSuite {
  val tests = Tests {
    test("fromCStringAndSize") {
      fromCStringAndSize(c"hello", 5) ==> "hello"
    }
    test("readStringBytesWith") {
      test("simple") {
        readStringBytesWith("hello") { buffer =>
          val string = fromCStringAndSize(buffer.pointer, buffer.contentLength)
          string ==> "hello"
        }
      }
      test("hello") {
        readStringBytesWith("Hello world!\n") { buffer =>
          buffer.get() ==> 'H'
          buffer.get() ==> 'e'
          buffer.get() ==> 'l'
          buffer.get() ==> 'l'
          buffer.get() ==> 'o'
          buffer.get() ==> ' '
          buffer.get() ==> 'w'
          buffer.get() ==> 'o'
          buffer.get() ==> 'r'
          buffer.get() ==> 'l'
          buffer.get() ==> 'd'
          buffer.get() ==> '!'
          buffer.get() ==> '\n'
        }
      }
      test("special characters") {
        val inputString = "㡮꼅犀渜㳣脝틑检⾸瓏䎎肋ࠧ똷䮴㾳䢷ᎄ灣㦯螳맱덣㾢髮큶侸좙瓨䂅⅗⠅汓ᠫ栎끄ḥ퉭᤹㡼"
        val builder = StringBuilder()
        readStringBytesWith(inputString) { buffer =>
          builder ++= fromCStringAndSize(buffer.pointer, buffer.contentLength)
        }
        builder.toString ==> inputString
      }
    }
    test("stringBytes") {
      test("simple") {
        val s = "hello"
        val buffer = stringBytes(s)
        buffer.get() ==> 'h'
        buffer.get() ==> 'e'
        buffer.get() ==> 'l'
        buffer.get() ==> 'l'
        buffer.get() ==> 'o'
        assertThrows[java.nio.BufferUnderflowException] { buffer.get() }
      }
      test("foo") {
        foo.bar()
      }
      test("other") {
        val buffer = stringBytes("text/plain")
      }
      test("special characters") {
        val inputString = "㡮꼅犀渜㳣脝틑检⾸瓏䎎肋ࠧ똷䮴㾳䢷ᎄ灣㦯螳맱덣㾢髮큶侸좙瓨䂅⅗⠅汓ᠫ栎끄ḥ퉭᤹㡼"
        val buffer = stringBytes(inputString)
        val result = fromCStringAndSize(buffer.pointer, buffer.contentLength)
        result ==> inputString
      }
      test("multiple calls") {
        val foo = stringBytes("foo")
        val bar = stringBytes("bar")
        val zar = stringBytes("zar")
        fromCStringAndSize(foo.pointer, foo.contentLength) ==> "foo"
        fromCStringAndSize(bar.pointer, bar.contentLength) ==> "bar"
        fromCStringAndSize(zar.pointer, zar.contentLength) ==> "zar"
      }
    }
    test("fromCStringAndSize") {
      val input = Array[Byte]('H', 'e', 'l', 'l', 'o', ' ', 'w', 'o', 'r', 'l', 'd')
      val expected = "Hello world"
      val result = fromCStringAndSize(input.at(0), input.length)

      result ==> expected
    }
  }
}
