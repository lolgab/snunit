package snunit.test

import utest._

object TapirTests extends TestSuite {
  val tests = Tests {
    test("tapir-helloworld") {
      withDeployedExample("tapir-helloworld") {
        tapirHelloWorldTest(baseUrl)
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
  def tapirHelloWorldTest(baseUrl: Uri) = {
    locally {
      val result = request.get(uri"$baseUrl/hello?name=Lorenzo").text()
      val expectedResult = "Hello Lorenzo!"
      assert(result == expectedResult)
    }
    locally {
      val resultResponse = request.get(uri"$baseUrl/inexistent")
      assert(resultResponse.statusCode() == 404)
    }
  }
}
