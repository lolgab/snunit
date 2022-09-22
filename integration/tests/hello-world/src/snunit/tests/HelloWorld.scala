package snunit.tests

import snunit._

object MyHandler extends Handler {
  val res = "Hello world!\n".getBytes
  def handleRequest(req: Request): Unit = {
    req.method match {
      case Method.GET =>
        val content =
          if (req.path.startsWith("/path")) req.path
          else if (req.path.startsWith("/version")) req.version
          else if (req.path.startsWith("/target")) req.target
          else if (req.path.startsWith("/query")) req.query
          else "Hello world!\n"
        req.send(
          statusCode = StatusCode.OK,
          content = content,
          headers = Seq("Content-Type" -> "text/plain")
        )
      case _ =>
        req.send(
          statusCode = StatusCode.NotFound,
          content = s"Not found\n",
          headers = Seq("Content-Type" -> "text/plain")
        )
    }
  }
}

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val s = SyncServerBuilder.build(MyHandler)
    s.listen()
  }
}
