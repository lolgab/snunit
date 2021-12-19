package snunit.tests

import snunit._

object MyHandler extends Handler {
  val res = "Hello world!\n".getBytes
  def handleRequest(req: Request): Unit = {
    req.method match {
      case Method.GET =>
        req.send(
          statusCode = StatusCode.OK,
          content = s"Hello world!\n",
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
