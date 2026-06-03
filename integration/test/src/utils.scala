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

def waitForAppReady(url: Uri = baseUrl, timeoutMs: Int = 60_000): Unit = {
  val deadline = System.currentTimeMillis() + timeoutMs
  var lastError = "unknown"
  while (System.currentTimeMillis() < deadline) {
    Try(simpleHttpClient.send(request.get(url).readTimeout(2.seconds))).toEither match {
      case Right(response) if response.code.code != 503 =>
        return
      case Right(_) =>
        lastError = "503 Service Unavailable"
      case Left(e) =>
        lastError = e.getMessage
    }
    Thread.sleep(100)
  }
  throw new RuntimeException(
    s"App at $url did not become ready within ${timeoutMs}ms (last error: $lastError)"
  )
}

def withDeployedExample[T](projectName: String, crossSuffix: String = "")(f: => T): T = {
  runMillCommand(s"integration.tests.$projectName$crossSuffix.deployTestApp")
  waitForAppReady()
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
  Thread.sleep(1000)
  try { f }
  finally { process.close() }
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
