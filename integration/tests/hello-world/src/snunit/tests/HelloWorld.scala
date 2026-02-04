package snunit.tests

import snunit.*

object MyHandler extends RequestHandler {
  val array = "Hello world!\n".getBytes
  def handleRequest(req: Request): Unit = {
    (req.method, req.path) match
      case Method.GET -> "/array" =>
        req.send(
          statusCode = StatusCode.OK,
          content = array,
          headers = Headers("Content-Type" -> "text/plain")
        )
      case Method.GET -> "/headers" =>
        req.send(
          statusCode = StatusCode.OK,
          content = "Request headers",
          headers = req.headers
        )
      case Method.GET -> path =>
        val content =
          if (path.startsWith("/path")) req.path
          else if (path.startsWith("/version")) req.version
          else if (path.startsWith("/target")) req.target
          else if (path.startsWith("/query")) req.query
          else if (path == "/empty") ""
          else "Hello world!\n"
        req.send(
          statusCode = StatusCode.OK,
          content = content,
          headers = Headers("Content-Type" -> "text/plain")
        )
      case Method.POST -> "/echo" =>
        req.send(
          statusCode = StatusCode.OK,
          content = req.contentRaw(),
          headers = Headers("Content-Type" -> "text/plain")
        )

      case _ =>
        req.send(
          statusCode = StatusCode.NotFound,
          content = s"Not found\n",
          headers = Headers("Content-Type" -> "text/plain")
        )
  }
}

object HelloWorld {
  def main(args: Array[String]): Unit = {
    SyncServerBuilder
      .setRequestHandler(MyHandler)
      .build()
      .listen()
  }
}
