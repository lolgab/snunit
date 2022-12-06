import utest._

import TestUtils._

object UndertowCaskTests extends TestSuite {
  val tests = Tests {
    test("undertow-helloworld") {
      withDeployedExampleMultiplatform("undertow-helloworld") {
        runOnAllPlatforms { baseUrl =>
          val result = requests.get(baseUrl).text()
          val expectedResult = "Hello World"
          assert(result == expectedResult)
        }
      }
    }
    test("cask-helloworld") {
      withDeployedExampleMultiplatform("cask-helloworld") {
        runOnAllPlatforms { baseUrl =>
          locally {
            val result = requests.get(baseUrl).text()
            val expectedResult = "Hello World!"
            assert(result == expectedResult)
          }

          locally {
            val result = requests.get(s"$baseUrl/hello?name=Lorenzo").text()
            val expectedResult = "Hello Lorenzo!"
            assert(result == expectedResult)
          }

          locally {
            val result = requests.post(s"$baseUrl/do-thing", data = "hello").text()
            val expectedResult = "olleh"
            assert(result == expectedResult)
          }
        }
      }
    }
  }
}
