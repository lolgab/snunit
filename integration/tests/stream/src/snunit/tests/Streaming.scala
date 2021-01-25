package snunit.tests

import snunit._

object Streaming {
  def main(args: Array[String]): Unit = {
    val server =
      SyncServerBuilder()
        .withRequestHandler(req => {
          req.method match {
            case Method.POST =>
              try {
                val json = ujson.read(req)
                req.send(
                  statusCode = StatusCode.OK,
                  content = json,
                  headers = Seq()
                )
              } catch {
                case e: ujson.ParseException =>
                  req.send(
                    statusCode = StatusCode.InternalServerError,
                    content = ujson.Obj("error" -> e.getMessage()),
                    headers = Seq()
                  )
              }
            case _ =>
              req.send(
                statusCode = StatusCode.NotFound,
                content = s"Not found\n",
                headers = Seq("Content-Type" -> "text/plain")
              )
          }
        })
        .build()

    server.listen()
  }
}
