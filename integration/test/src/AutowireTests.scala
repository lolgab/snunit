package snunit.test

import utest._

object AutowireTests extends TestSuite {
  val tests = Tests {
    test("autowire") {
      withDeployedExample("autowire") {
        val name = "A-Name"
        val asyncResult =
          request.post(uri"$baseUrl/snunit/tests/MyApi/helloAsync").body(s"""{"name": "$name"}""").text()
        assert(asyncResult.contains("Hello "))
        assert(asyncResult.contains(name))
      }
    }
    test("autowire-int") {
      withDeployedExample("autowire-int") {
        val asyncResult = request.post(uri"$baseUrl/snunit/tests/MyApi/addOne").body(s"""{"i": 1}""").text()
        assert(asyncResult == "2")
      }
    }
  }
}
