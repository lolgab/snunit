package snunit.test

import utest._

object BaseTests extends TestSuite {
  val tests = Tests {
    test("hello-world") {
      withDeployedExampleCross("hello-world") {
        locally {
          val result = request.get(baseUrl).text()
          val expectedResult = "Hello world!\n"
          assert(result == expectedResult)
        }
        locally {
          val result = request.get(uri"$baseUrl/version").text()
          val expectedResult = "HTTP/1.1"
          assert(result == expectedResult)
        }
        locally {
          val result = request.get(baseUrl.withPath("target", "%2F%2f%5C%5c").pathSegmentsEncoding(identity)).text()
          val expectedResult = "/target/%2F%2f%5C%5c"
          assert(result == expectedResult)
        }
        locally {
          val result =
            request.get(baseUrl.withPath("path", "foo%2Fbar%2f%5C%5c").pathSegmentsEncoding(identity)).text()
          val expectedResult = """/path/foo/bar/\\"""
          assert(result == expectedResult)
        }
        locally {
          val result = request.get(uri"$baseUrl/empty").text()
          val expectedResult = ""
          assert(result == expectedResult)
        }
        locally {
          val responseHeaders = request
            .get(uri"$baseUrl/headers")
            .header("foo", "bar")
            .header("foo", "qux")
            .header("bla", "bal")
            .responseHeaders()
            .toSet

          assert(responseHeaders.contains(Header("foo", "bar")))
          assert(responseHeaders.contains(Header("foo", "qux")))
          assert(responseHeaders.contains(Header("bla", "bal")))
        }
      }
    }
    test("async") {
      withDeployedExample("async") {
        val result = request.get(baseUrl).text()
        val expectedResult = "Hello world async!\n"
        assert(result == expectedResult)
      }
    }
    test("async-epollcat") {
      withDeployedExample("async-epollcat") {
        val result = request.get(baseUrl).text()
        val expectedResult = "Hello world from epollcat!"
        assert(result == expectedResult)
      }
    }
    test("async-epollcat-unit-requests-limits") {
      withDeployedExample("async-epollcat") {
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
          val expectedResult = "Hello world from epollcat!"
          assert(result == expectedResult)
        }
      }
    }
    test("multiple-handlers") {
      withDeployedExample("multiple-handlers") {
        val getResult = request.get(baseUrl).text()
        val expectedGetResult = "Hello world multiple handlers!\n"
        assert(getResult == expectedGetResult)

        val postResultResponse = request.post(baseUrl)
        val postResult = postResultResponse.text()
        val expectedPostResult = "Not found\n"
        assert(postResult == expectedPostResult)
        assert(postResultResponse.statusCode() == 404)
      }
    }
    test("async-multiple-handlers") {
      withDeployedExample("async-multiple-handlers") {
        val getResult = request.get(baseUrl).text()
        val expectedGetResult = "Hello world async multiple handlers!\n"
        assert(getResult == expectedGetResult)

        val postResultResponse = request.post(baseUrl)
        val postResult = postResultResponse.text()
        val expectedPostResult = "Not found\n"
        assert(postResult == expectedPostResult)
        assert(postResultResponse.statusCode() == 404)
      }
    }
  }
}
