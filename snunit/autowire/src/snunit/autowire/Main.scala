package snunit.snautowire

import snunit._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import upickle.default._
import autowire.Serializers
import upickle.Api
import scala.concurrent.Future


object UpickleAutowireServer extends autowire.Server[ujson.Value, Reader, Writer] {
  override def write[Result: Writer](r: Result): ujson.Value = upickle.default.write(r)
  override def read[Result: Reader](p: ujson.Value): Result = upickle.default.read[Result](p)
}

package object snautowire {
  def createServer[T](router: autowire.Server[ujson.Value, Reader, Writer]#Router): Server = {
    ServerBuilder
      .withRequestHandler(req => {
        req.method match {
          case Method.POST =>
            req.path.split("/").toList match {
              case "" :: segments =>
                val content = req.content
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
              case _ =>
                req.send(404, "Not found", Seq.empty)
            }
          case _ =>
            req.send(404, "Not found", Seq.empty)
        }
      })
      .build()
  }
} 

trait MyApi {
  def helloAsync(name: String): Future[String]
  def helloSync(name: String): String
}

object MyApiImpl extends MyApi {
  def helloAsync(name: String): Future[String] = Future.successful(s"Hello $name")
  def helloSync(name: String): String = s"Hello $name"
}

object Main {
  def main(args: Array[String]): Unit = {
    val server = snautowire.createServer(UpickleAutowireServer.route[MyApi](MyApiImpl))

    while (true) {
      scala.scalanative.runtime.loop()
      server.runOnce()
    }

  }
}
