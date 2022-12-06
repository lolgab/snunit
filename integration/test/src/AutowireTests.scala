import utest._

import TestUtils._

object AutowireTests extends TestSuite {
  val tests = Tests {
    test("autowire") {
      withDeployedExample("autowire") {
        val name = "A-Name"
        val asyncResult =
          requests.post(s"$baseUrl/snunit/tests/MyApi/helloAsync", data = s"""{"name": "$name"}""").text()
        assert(asyncResult.contains("Hello "))
        assert(asyncResult.contains(name))
      }
    }
    test("autowire-int") {
      withDeployedExample("autowire-int") {
        val asyncResult = requests.post(s"$baseUrl/snunit/tests/MyApi/addOne", data = s"""{"i": 1}""").text()
        assert(asyncResult == "2")
      }
    }
  }
}
