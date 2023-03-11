package snunit.Autowire

import snunit._
import upickle.default._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object UpickleAutowireServer extends autowire.Server[String, Reader, Writer] {
  override def write[Result: Writer](r: Result): String = upickle.default.write(r)
  override def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)
}
class AutowireHandler(router: autowire.Server[String, Reader, Writer]#Router, next: RequestHandler)
    extends RequestHandler {
  protected def onError(req: Request, e: Exception): Unit = e match {
    case _: ujson.ParsingFailedException =>
      req.send(
        statusCode = StatusCode.BadRequest,
        s"Failed to parse json body: ${req.content}",
        Seq("Content-type" -> "text/plain")
      )
    case e: MatchError =>
      req.send(
        statusCode = StatusCode.BadRequest,
        content = s"Invalid request: $e",
        headers = Seq("Content-Type" -> "text/plain")
      )
    case e =>
      req.send(
        statusCode = StatusCode.InternalServerError,
        content = s"Got error: $e",
        headers = Seq("Content-Type" -> "text/plain")
      )
  }

  def handleRequest(req: Request): Unit = {
    if (req.method == Method.POST) {
      req.target.split("/").toList match {
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
            .send(
              statusCode = StatusCode.OK,
              content = result,
              headers = Seq("Content-Type" -> "application/json")
            )

          future.recover { case e: Exception =>
            onError(req, e)
          }
        case _ => next.handleRequest(req)
      }
    } else next.handleRequest(req)
  }
}
