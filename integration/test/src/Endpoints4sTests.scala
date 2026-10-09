package snunit.test

import utest._

object Endpoints4sTests extends TestSuite {
  val tests = Tests {
    test("endpoints4s-helloworld") {
      withDeployedExample("endpoints4s-helloworld", readyUrl = uri"$baseUrl/hello?name=Lorenzo") {
        locally {
          val result = request.get(uri"$baseUrl/hello?name=Lorenzo").text()
          assert(result == "Hello Lorenzo!")
        }
        locally {
          val response = request.get(uri"$baseUrl/hello")
          assert(response.statusCode() == 400)
        }
        locally {
          val response = request.get(uri"$baseUrl/inexistent")
          assert(response.statusCode() == 404)
        }
      }
    }
  }
}
