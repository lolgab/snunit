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
    "--ticker",
    "false",
    "show",
    command
  )
  .call(
    cwd = os.Path(sys.env("MILL_WORKSPACE_ROOT"))
  )

def withDeployedExample[T](projectName: String, crossSuffix: String = "")(f: => T): T = {
  val Vector(s"\"$_:$_:$_:$nativeBinary\"") =
    runMillCommand(s"integration.tests.$projectName$crossSuffix.nativeLink").out.lines(): @unchecked
  val workspace = os.Path(sys.env("MILL_WORKSPACE_ROOT"))
  val process2 = os.proc(nativeBinary).spawn(cwd = workspace)
  Thread.sleep(1000)
  try { f }
  finally { process2.close() }
}
def withDeployedExampleHttp4s(projectName: String)(f: => Unit) = {
  BuildInfo.http4sVersions.split(':').foreach { versions =>
    withDeployedExample(projectName, s"[$versions]")(f)
  }
}
def withDeployedExampleMultiplatform(projectName: String)(f: => Unit) = {
  val projectPrefix = s"integration.tests.$projectName"

  // Run test against JVM version
  val Vector(s"\"$_:$_:$_:$path\"") = runMillCommand(s"$projectPrefix.jvm.launcher").out.lines(): @unchecked
  val process1 = os.proc(path).spawn()
  Thread.sleep(1000)
  try { f }
  finally { process1.close() }

  val Vector(s"\"$_:$_:$_:$nativeBinary\"") =
    runMillCommand(s"$projectPrefix.native.nativeLink").out.lines(): @unchecked
  val process2 = os.proc(nativeBinary).spawn()
  Thread.sleep(1000)
  try { f }
  finally { process2.close() }

}

private val futureBackend = HttpClientFutureBackend()

val baseUrl = uri"http://localhost:8080"
val websocketBaseUrl = uri"ws://localhost:8080"

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
