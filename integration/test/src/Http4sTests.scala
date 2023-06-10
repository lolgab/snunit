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
            .proc(
              if (sys.env.contains("CI")) Seq("sudo") else Seq.empty[String],
              "curl",
              "-s",
              "--unix-socket",
              BuildInfo.unitControl,
              "localhost/control/applications/app/restart"
            )
            .call()
            .out
            .text()
            .replaceAll("\\s+", "")
          assert(restartResult == """{"success":"Ok"}""")
          Thread.sleep(1000)
          val result = request.get(baseUrl).text()
          val expectedResult = "Hello Http4s App!"
          assert(result == expectedResult)
        }
      }
    }
    test("unit-requests-limits") {
      withDeployedExample("http4s-app") {
        val limitsResult = os
          .proc(
            if (sys.env.contains("CI")) Seq("sudo") else Seq.empty[String],
            "curl",
            "-s",
            "--unix-socket",
            BuildInfo.unitControl,
            "-XPUT",
            "-d",
            """{"requests": 1}""",
            "localhost/config/applications/app/limits"
          )
          .call()
          .out
          .text()
          .replaceAll("\\s+", "")
        assert(limitsResult == """{"success":"Reconfigurationdone."}""")
        Thread.sleep(1000)
        for (i <- 0.to(10)) {
          val result = request.get(baseUrl).text()
          val expectedResult = "Hello Http4s App!"
          assert(result == expectedResult)
        }
      }
    }
  }
}
