package snunit.examples

import snunit._
import ujson._

object StreamingExample {
  def main(args: Array[String]): Unit = {
    val server =
      SyncServerBuilder()
        .withRequestHandler(req => {
          req.method match {
            case Method.POST =>
              try {
                val json = ujson.read(req)
                req.send(
                  statusCode = 200,
                  content = json,
                  headers = Seq()
                )
              } catch {
                case e: ujson.ParseException =>
                  req.send(
                    statusCode = 500,
                    content = ujson.Obj("error" -> e.getMessage()),
                    headers = Seq()
                  )
              }
            case _ =>
              req.send(
                statusCode = 404,
                content = s"Not found\n",
                headers = Seq("Content-Type" -> "text/plain")
              )
          }
        })
        .build()

    server.listen()
  }
}
