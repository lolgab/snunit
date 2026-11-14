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
    test("http4s-concurrency-limit") {
      withDeployedExample("http4s-concurrency-limit", readyUrl = uri"$baseUrl/max") {
        def slowRequests(): List[String] = {
          val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
          try {
            val calls = (1 to 8).map(_ => pool.submit(() => request.get(uri"$baseUrl/slow").text()))
            calls.map(_.get()).toList
          } finally pool.shutdown()
        }
        // The first request from each thread of the router waits in libunit for the port of the reply, and it isn't
        // counted until it runs, so the first requests of a process can go over the limit
        val first = slowRequests()
        request.get(uri"$baseUrl/reset").text()
        val second = slowRequests()
        // Every request waiting in the queue of FreeUnit ran when a running one was done
        assert(first == List.fill(8)("done"), second == List.fill(8)("done"))
        assert(request.get(uri"$baseUrl/max").text() == "2")
      }
    }
    test("http4s-otel4s") {
      withDeployedExample("http4s-otel4s", readyUrl = uri"$baseUrl/hello?name=Lorenzo") {
        otel4sTracingTest(uri"$baseUrl/hello?name=Lorenzo")
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
          waitUntilReady()
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
        waitUntilReady()
        for (i <- 0.to(10)) {
          val result = request.get(baseUrl).text()
          val expectedResult = "Hello Http4s App!"
          assert(result == expectedResult)
        }
      }
    }
  }
}
