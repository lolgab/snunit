import utest._

import TestUtils._

object MainTest extends TestSuite {
  val tests = Tests {
    test("hello-world") {
      withDeployedExample("hello-world") {
        val result = requests.get(baseUrl).text()
        val expectedResult = "Hello world!\n"
        assert(result == expectedResult)
      }
    }
    test("async") {
      withDeployedExample("async") {
        val result = requests.get(baseUrl, readTimeout = 10000).text()
        val expectedResult = "Hello world async!\n"
        assert(result == expectedResult)
      }
    }
    test("multiple-handlers") {
      withDeployedExample("multiple-handlers") {
        val getResult = requests.get(baseUrl).text()
        val expectedGetResult = "Hello world multiple handlers!\n"
        assert(getResult == expectedGetResult)

        val postResultResponse = requests.post(baseUrl, check = false)
        val postResult = postResultResponse.text()
        val expectedPostResult = "Not found\n"
        assert(postResult == expectedPostResult)
        assert(postResultResponse.statusCode == 404)
      }
    }
    test("async-multiple-handlers") {
      withDeployedExample("async-multiple-handlers") {
        val getResult = requests.get(baseUrl).text()
        val expectedGetResult = "Hello world async multiple handlers!\n"
        assert(getResult == expectedGetResult)

        val postResultResponse = requests.post(baseUrl, check = false)
        val postResult = postResultResponse.text()
        val expectedPostResult = "Not found\n"
        assert(postResult == expectedPostResult)
        assert(postResultResponse.statusCode == 404)
      }
    }
    test("autowire") {
      withDeployedExample("autowire") {
        val name = "A-Name"
        val asyncResult =
          requests.post(s"$baseUrl/snunit/tests/MyApi/helloAsync", data = s"""{"name": "$name"}""").text()
        assert(asyncResult.contains("Hello "))
        assert(asyncResult.contains(name))
      }
    }
    test("stream") {
      withDeployedExample("stream") {
        val json = """{"message":"Hello world!"}"""
        val asyncResult = requests.post(s"$baseUrl", data = json).text()
        assert(asyncResult == json)
      }
    }
  }
}
