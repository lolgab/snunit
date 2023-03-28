package snunit

import utest.*
import scala.collection.mutable

object HeadersTests extends TestSuite {
  val tests = Tests {
    test("length") {
      Headers("one" -> "1", "two" -> "2").length ==> 2
    }
    test("fieldsLength") {
      Headers("one" -> "1", "two" -> "2").fieldsLength ==> 8
    }
    test("name") {
      val headers = Headers("one" -> "1", "two" -> "2")
      headers.name(0) ==> "one"
      headers.name(1) ==> "two"
    }
    test("value") {
      val headers = Headers("one" -> "1", "two" -> "2")
      headers.value(0) ==> "1"
      headers.value(1) ==> "2"
    }
    test("updateName") {
      val headers = Headers(1)
      headers.updateName(0, "name")
      headers.name(0) ==> "name"
    }
    test("updateValue") {
      val headers = Headers(1)
      headers.updateValue(0, "value")
      headers.value(0) ==> "value"
    }
    test("foreach") {
      val headers = Headers("one" -> "1", "two" -> "2")
      val map = mutable.Map.empty[String, String]
      headers.foreach((name, value) => map(name) = value)
      map("one") ==> "1"
      map("two") ==> "2"
      map.size ==> 2
    }
  }
}
