package snunit

import scala.concurrent.ExecutionContext.Implicits.global
import upickle.default._
import autowire.Serializers
import upickle.Api
import scala.concurrent.Future

object Autowire {
  object UpickleAutowireServer extends autowire.Server[ujson.Value, Reader, Writer] {
    override def write[Result: Writer](r: Result): ujson.Value = upickle.default.write(r)
    override def read[Result: Reader](p: ujson.Value): Result = upickle.default.read[Result](p)
  }
  implicit class ServerBuilderAutowireOps(val builder: ServerBuilder) extends AnyVal {
    def withAutowireRouter(router: autowire.Server[ujson.Value, Reader, Writer]#Router): ServerBuilder = {
      builder
        .withRequestHandler(req => {
          if (req.method == Method.POST) {
            req.path.split("/").toList match {
              case "" :: segments =>
                val content = req.content
                System.err.println(content)
                val resultFuture = router(
                  autowire.Core.Request(
                    segments,
                    ujson.read(content).obj.toMap.mapValues(_.render())
                  )
                )
                resultFuture.onComplete {
                  case scala.util.Success(result) =>
                    req.send(
                      statusCode = 200,
                      content = ujson.write(result),
                      headers = Seq("Content-Type" -> "text/json")
                    )
                  case scala.util.Failure(error) =>
                    req.send(500, s"Got error: $error", Seq.empty)
                }
                true
              case _ =>
                false
            }
          } else false
        })
    }
  }
}
