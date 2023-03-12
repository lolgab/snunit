package snunit.test

import utest._

object Http4sTests extends TestSuite {
  val tests = Tests {
    test("http4s") {
      withDeployedExampleHttp4s("http4s-helloworld") {
        val result = request.get(baseUrl).text()
        val expectedResult = "Hello Http4s!"
        assert(result == expectedResult)
      }
    }
  }
}
