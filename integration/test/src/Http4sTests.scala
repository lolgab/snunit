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
    test("http4s-app") {
      withDeployedExample("http4s-app") {
        val result = request.get(baseUrl).text()
        val expectedResult = "Hello Http4s App!"
        assert(result == expectedResult)
      }
    }
    test("unit-restart") {
      withDeployedExample("http4s-app") {
        for (i <- 0.to(3)) {
          val restartResult = os
            .proc("curl", "-s", "--unix-socket", BuildInfo.unitControl, "localhost/control/applications/app/restart")
            .call()
            .out
            .text()
            .replaceAll("\\s+", "")
          assert(restartResult == """{"success":"Ok"}""")
          Thread.sleep(100)
          val result = request.get(baseUrl).text()
          val expectedResult = "Hello Http4s App!"
          assert(result == expectedResult)
        }
      }
    }
  }
}
