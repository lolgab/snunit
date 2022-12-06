import utest._

import TestUtils._

object TapirTests extends TestSuite {
  val tests = Tests {
    test("tapir-helloworld") {
      withDeployedExampleMultiplatform("tapir-helloworld") {
        runOnAllPlatforms { baseUrl =>
          tapirHelloWorldTest(baseUrl)
        }
      }
    }
    test("tapir-helloworld-future") {
      withDeployedExample("tapir-helloworld-future") {
        tapirHelloWorldTest(baseUrl)
      }
    }
    test("tapir-helloworld-cats") {
      withDeployedExample("tapir-helloworld-cats") {
        tapirHelloWorldTest(baseUrl)
      }
    }
  }
  def tapirHelloWorldTest(baseUrl: String) = {
    locally {
      val result = requests.get(s"$baseUrl/hello?name=Lorenzo").text()
      val expectedResult = "Hello Lorenzo!"
      assert(result == expectedResult)
    }
    locally {
      val resultResponse = requests.get(s"$baseUrl/inexistent", check = false)
      assert(resultResponse.statusCode == 404)
    }
  }
}
