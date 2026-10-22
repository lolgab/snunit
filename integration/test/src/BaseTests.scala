package snunit.test

import utest._

object BaseTests extends TestSuite {
  val tests = Tests {
    test("hello-world") {
      withDeployedExample("hello-world") {
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
          // A client that starts reading late makes the shm of the application fill up
          val socket = new java.net.Socket("localhost", BuildInfo.port.toInt)
          try {
            socket.getOutputStream.write("GET /large HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes)
            socket.getOutputStream.flush()
            Thread.sleep(3000)
            val all = socket.getInputStream.readAllBytes()
            val bodyStart = new String(all, "ISO-8859-1").indexOf("\r\n\r\n") + 4
            val body = all.drop(bodyStart)
            assert(body.length == 160 * 1024 * 1024)
            assert(body.indices.forall(i => body(i) == ((i / (1024 * 1024)) % 251).toByte))
          } finally socket.close()
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
  }
}
