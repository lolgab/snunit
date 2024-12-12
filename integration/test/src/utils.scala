package snunit.test

import scala.concurrent.Future
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
    "--disable-ticker",
    "show",
    command
  )
  .call(
    cwd = os.Path(sys.env("MILL_WORKSPACE_ROOT"))
  )

def withDeployedExample[T](projectName: String, crossSuffix: String = "")(f: => T): T = {
  runMillCommand(s"integration.tests.$projectName$crossSuffix.deployTestApp")
  f
}
def withDeployedExampleHttp4s(projectName: String)(f: => Unit) = {
  BuildInfo.http4sVersions.split(':').foreach { versions =>
    withDeployedExample(projectName, s"[$versions]")(f)
  }
}
def withDeployedExampleMultiplatform(projectName: String, crossSuffix: String = "")(f: => Unit) = {
  val projectPrefix = s"integration.tests.$projectName$crossSuffix"
  runMillCommand(s"$projectPrefix.native.deployTestApp")
  val result = runMillCommand(s"$projectPrefix.jvm.launcher").out.lines().head
  val s""""$_:$_:$_:$path"""" = result: @unchecked
  val process = os.proc(path).spawn()
  Thread.sleep(1000)
  try { f }
  finally { process.close() }
}
def withDeployedExampleMultiplatformCross(projectName: String)(f: => Unit) = {
  BuildInfo.scalaVersions.split(':').foreach { scalaVersion =>
    withDeployedExampleMultiplatform(projectName, s"[$scalaVersion]")(f)
  }
}
def withDeployedExampleCross(projectName: String)(f: => Unit) = {
  BuildInfo.scalaVersions.split(':').foreach { scalaVersion =>
    withDeployedExample(projectName, s"[$scalaVersion]")(f)
  }
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
