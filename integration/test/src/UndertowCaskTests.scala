package snunit.test

import utest._

object UndertowCaskTests extends TestSuite {
  val tests = Tests {
    test("undertow-helloworld") {
      withDeployedExampleMultiplatform("undertow-helloworld") {
        val result = request.get(baseUrl).text()
        val expectedResult = "Hello World"
        assert(result == expectedResult)
      }
    }
    test("cask-helloworld") {
      withDeployedExampleMultiplatform("cask-helloworld") {
        locally {
          val result = request.get(baseUrl).text()
          val expectedResult = "Hello World!"
          assert(result == expectedResult)
        }

        locally {
          val result = request.get(uri"$baseUrl/hello?name=Lorenzo").text()
          val expectedResult = "Hello Lorenzo!"
          assert(result == expectedResult)
        }

        locally {
          val result = request.post(uri"$baseUrl/do-thing").body("hello").text()
          val expectedResult = "olleh"
          assert(result == expectedResult)
        }
      }
    }
  }
}
