package snunit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import upickle.default._

object Autowire {
  object UpickleAutowireServer extends autowire.Server[String, Reader, Writer] {
    override def write[Result: Writer](r: Result): String = upickle.default.write(r)
    override def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)
  }
  implicit class ServerBuilderAutowireOps(private val builder: AsyncServerBuilder) extends AnyVal {
    def withAutowireRouter(router: autowire.Server[String, Reader, Writer]#Router): AsyncServerBuilder = {
      builder
        .withRequestHandler(req => {
          if (req.method == Method.POST) {
            req.path.split("/").toList match {
              case "" :: segments =>
                val resultFuture = router(
                  autowire.Core.Request(
                    segments,
                    ujson.read(req.contentRaw).obj.mapValues(ujson.write(_)).toMap
                  )
                )
                resultFuture.onComplete {
                  case scala.util.Success(result) =>
                    req.send(
                      statusCode = 200,
                      content = result,
                      headers = Seq("Content-Type" -> "application/json")
                    )
                  case scala.util.Failure(error) =>
                    req.send(500, s"Got error: $error", Seq.empty)
                }
              case _ => req.next()
            }
          } else req.next()
        })
    }
  }
}
