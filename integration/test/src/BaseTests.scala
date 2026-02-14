package snunit.test

import utest._
import scala.concurrent.duration._

object BaseTests extends TestSuite {
  val tests = Tests {

    test("hello-world") {
      val helloWorldExample = Example("hello-world")
      test("hello") {
        helloWorldExample.running {
          val result = request.get(baseUrl).text()
          val expectedResult = "Hello world!\n"
          assert(result == expectedResult)
        }
      }
      test("version") {
        helloWorldExample.running {
          val result = request.get(uri"$baseUrl/version").text()
          val expectedResult = "HTTP/1.1"
          assert(result == expectedResult)
        }
      }
      test("target") {
        helloWorldExample.running {
          val result = request.get(baseUrl.withPath("target", "%2F%2f%5C%5c").pathSegmentsEncoding(identity)).text()
          val expectedResult = "/target/%2F%2f%5C%5c"
          assert(result == expectedResult)
        }
      }
      test("path") {
        helloWorldExample.running {
          val result =
            request.get(baseUrl.withPath("path", "foo%2Fbar%2f%5C%5c").pathSegmentsEncoding(identity)).text()
          val expectedResult = """/path/foo/bar/\\"""
          assert(result == expectedResult)
        }
      }
      test("empty") {
        helloWorldExample.running {
          val result = request.get(uri"$baseUrl/empty").text()
          val expectedResult = ""
          assert(result == expectedResult)
        }
      }
      test("async") {
        helloWorldExample.running {
          /* Hit /async multiple times on same client to exercise keep-alive and handler_done pipe (no double wake). */
          val expectedResult = "Hello world!\n"
          (1 to 50).foreach { _ =>
            val result = request.get(uri"$baseUrl/async").text()
            assert(result == expectedResult)
          }
        }
      }
      test("echo") {
        helloWorldExample.running {
          val result = request.post(uri"$baseUrl/echo").body("hello").text()
          val expectedResult = "hello"
          assert(result == expectedResult)
        }
      }
      test("headers") {
        helloWorldExample.running {
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
      test("close") {
        helloWorldExample.running {
          /* Reproduce wrk-style termination: connect, send GET /async, close socket without reading.
           * Server sees client disconnect (FIN/RST) while async handler may still be running. */
          val host = baseUrl.host.get
          val port = baseUrl.port.get
          val requestBytes =
            s"GET /async HTTP/1.1\r\nHost: $host:$port\r\nConnection: keep-alive\r\n\r\n".getBytes(
              java.nio.charset.StandardCharsets.US_ASCII
            )

          (1 to 2).foreach { _ =>
            val socket = new java.net.Socket()
            try {
              socket.connect(new java.net.InetSocketAddress(host, port), 1000)
              socket.setSoTimeout(1000)
              socket.getOutputStream.write(requestBytes)
              socket.getOutputStream.flush()
              // Close immediately without reading response (like wrk on process exit).
              socket.close()
            } catch { case _: Exception => /* ignore */ }
            finally
              if (!socket.isClosed)
                try socket.close()
                catch { case _: Exception => }
          }

          // Server should still be alive: a normal request must succeed.
          val result = request.get(uri"$baseUrl/async").readTimeout(1.second).text()
          assert(result == "Hello world!\n")
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
