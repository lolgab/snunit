package snunit.test

import scala.concurrent.duration._
import utest._

object TapirTests extends TestSuite {
  val tests = Tests {
    test("tapir-helloworld") {
      withDeployedExample("tapir-helloworld", readyUrl = uri"$baseUrl/hello?name=Lorenzo") {
        tapirHelloWorldTest(baseUrl)
      }
    }
    test("tapir-helloworld-cats-effect") {
      withDeployedExample("tapir-helloworld-cats-effect", readyUrl = uri"$baseUrl/hello?name=Lorenzo") {
        tapirHelloWorldTest(baseUrl)
      }
    }
    test("tapir-otel4s") {
      withDeployedExample("tapir-otel4s", readyUrl = uri"$baseUrl/hello?name=Lorenzo") {
        tapirOtel4sTest()
      }
    }
    test("tapir-app") {
      withDeployedExample("tapir-app", readyUrl = uri"$baseUrl/hello?name=Lorenzo") {
        tapirHelloWorldTest(baseUrl)
      }
    }
  }
  def tapirOtel4sTest() = {
    // The application exports to http://localhost:4318 (the OTLP default) when SNUnit sets no endpoint
    val received = new java.util.concurrent.CopyOnWriteArrayList[Array[Byte]]
    val server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 4318), 0)
    server.createContext(
      "/v1/traces",
      exchange => {
        received.add(exchange.getRequestBody.readAllBytes())
        exchange.sendResponseHeaders(200, -1)
        exchange.close()
      }
    )
    server.start()
    try {
      def bytes(hex: String) = hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray
      def contains(body: Array[Byte], needle: Array[Byte]) = body.indexOfSlice(needle) >= 0
      val traceId = "0af7651916cd43dd8448eb211c80319c"
      val parentSpanId = "b7ad6b7169203331"
      val result = request
        .get(uri"$baseUrl/hello?name=Lorenzo")
        .header("traceparent", s"00-$traceId-$parentSpanId-01")
        .text()
      assert(result == "Hello Lorenzo!")
      val deadline = System.nanoTime() + 30.seconds.toNanos
      def exported = received.toArray.toSeq.map(_.asInstanceOf[Array[Byte]])
      // Both spans, the one of the request and the one created by the application, are in the trace of the header
      def done = exported.exists(b =>
        contains(b, bytes(traceId)) && contains(b, bytes(parentSpanId)) && contains(b, "greet".getBytes("UTF-8"))
      )
      while (!done && System.nanoTime() < deadline) Thread.sleep(200)
      assert(done)
    } finally server.stop(0)
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
