package snunit.tests

import snunit._
import snunit.snunitzio._
import zio._
import zio.console._
import zio.clock._
import zio.duration._

object Main {

  def handler(req: Request) = (
    for {
      _ <- putStrLn(s"Got request: ${req.content}")
      _ <- sleep(100.millis)
    } yield Response(StatusCode.OK, "Hello from ZIO", Seq.empty)
  ).provideLayer(ZEnv.live)

  def main(args: Array[String]): Unit = {
    AsyncServerBuilder()
      .withZIORequestHandler(handler)
      .build()
  }
}
