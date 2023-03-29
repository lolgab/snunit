package snunit.tests

import snunit.*

object MyHandler extends RequestHandler {
  val array = "Hello world!\n".getBytes
  def handleRequest(req: Request): Unit = {
    req.method match {
      case Method.GET =>
        val path = req.path
        if (path == "/array")
          req.send(
            statusCode = StatusCode.OK,
            content = array,
            headers = Headers("Content-Type" -> "text/plain")
          )
        else
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
      case _ =>
        req.send(
          statusCode = StatusCode.NotFound,
          content = s"Not found\n",
          headers = Headers("Content-Type" -> "text/plain")
        )
    }
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
