package snunit

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import upickle.default._

object Autowire {
  object UpickleAutowireServer extends autowire.Server[String, Reader, Writer] {
    override def write[Result: Writer](r: Result): String = upickle.default.write(r)
    override def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)
  }
  implicit class ServerBuilderAutowireOps(private val builder: AsyncServerBuilder) extends AnyVal {
    private def onError(req: Request, e: Exception): Unit = e match {
      case _: ujson.ParsingFailedException =>
        req.send(
          statusCode = 400,
          s"Failed to parse json body: ${req.content}",
          Seq("Content-type" -> "text/plain")
        )
      case e: MatchError =>
        req.send(
          statusCode = 400,
          content = s"Invalid request: $e",
          headers = Seq("Content-Type" -> "text/plain")
        )
      case e =>
        req.send(statusCode = 500, content = s"Got error: $e", headers = Seq("Content-Type" -> "text/plain"))
    }
    def withAutowireRouter(
        router: autowire.Server[String, Reader, Writer]#Router,
        onError: (Request, Exception) => Unit = onError
    ): AsyncServerBuilder = {
      builder
        .withRequestHandler(req => {
          if (req.method == Method.POST) {
            req.path.split("/").toList match {
              case "" :: segments =>
                val future = for {
                  args <- Future.fromTry(Try(ujson.read(req.contentRaw).obj.mapValues(ujson.write(_)).toMap))
                  result <- router(
                    autowire.Core.Request(
                      segments,
                      args
                    )
                  )
                } yield req
                  .send(statusCode = 200, content = result, headers = Seq("Content-Type" -> "application/json"))

                future.recover { case e: Exception =>
                  onError(req, e)
                }
              case _ => req.next()
            }
          } else req.next()
        })
    }
  }
}
