package snunit.test

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.chaining._
import scala.util.control.NonFatal
import scala.util.Try
import sttp.client3.quick._
import sttp.client3.HttpClientFutureBackend

private def runMillCommand(command: String) = os
  .proc(
    "./mill",
    // adding `-i` breaks the ability to close unitd processes
    "--no-build-lock",
    "--ticker",
    "false",
    "show",
    command
  )
  .call(
    cwd = os.Path(sys.env("MILL_WORKSPACE_ROOT"))
  )

// Unit answers 503 (or refuses connections) until the application process is up
def waitUntilReady(url: Uri = baseUrl): Unit = {
  val deadline = System.nanoTime() + 60.seconds.toNanos
  while (Try(simpleHttpClient.send(request.get(url).readTimeout(2.seconds))).toOption.forall(_.code.code == 503)) {
    if (System.nanoTime() > deadline) throw new RuntimeException(s"$url is not ready")
    Thread.sleep(200)
  }
}

def withDeployedExample[T](projectName: String, crossSuffix: String = "", readyUrl: Uri = baseUrl)(f: => T): T = {
  runMillCommand(s"integration.tests.$projectName$crossSuffix.deployTestApp")
  waitUntilReady(readyUrl)
  f
}
def withDeployedExampleHttp4s(projectName: String)(f: => Unit) = {
  BuildInfo.http4sVersions.split(':').foreach { versions =>
    withDeployedExample(projectName, s"[$versions]")(f)
  }
}
def withDeployedExampleMultiplatform(projectName: String)(f: => Unit) = {
  val projectPrefix = s"integration.tests.$projectName"
  runMillCommand(s"$projectPrefix.native.deployTestApp")
  val result = runMillCommand(s"$projectPrefix.jvm.launcher").out.lines().head
  val s""""$_:$_:$_:$path"""" = result: @unchecked
  val process = os.proc(path).spawn()
  runOnAllPlatforms(waitUntilReady)
  try { f }
  finally { process.close() }
}

/** The application is traced with otel4s: its spans reach an OTLP receiver in the trace of the `traceparent` header. */
def otel4sTracingTest(url: Uri): Unit = {
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
      .get(url)
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

private val futureBackend = HttpClientFutureBackend()

val baseUrl = uri"http://localhost:${BuildInfo.port}"
val websocketBaseUrl = uri"ws://localhost:${BuildInfo.port}"

def runOnAllPlatforms(f: Uri => Unit) = {
  Seq(uri"http://localhost:8080", baseUrl).foreach(f)
}

def request = quickRequest

type Req = sttp.client3.Request[String, Any]

private def sendReq(req: Req) = simpleHttpClient.send(req)

extension (req: Req)
  def text() = sendReq(req).body
  def responseHeaders() = sendReq(req).headers
  def statusCode() = sendReq(req).code.code
  def websocket() =
    req
      .response(asWebSocketAlwaysUnsafe[Future])
      .send(futureBackend)

export sttp.client3.quick.UriContext

export sttp.model.Uri
export sttp.model.Header

export scala.concurrent.Future

export sttp.ws.{WebSocketFrame as Frame}

export scala.concurrent.ExecutionContext.Implicits.global
