package snunit.tests

import snunit._
import snunit.snunitzio._
import zio.clock._
import zio.console._
import zio.duration._

object Main {
  def handler(req: ZIORequest) = for {
    _ <- sleep(100.millis)
    _ <- putStrLn(s"Got request: ${req.content}")
    _ <- sleep(100.millis)
  } yield Response(StatusCode.OK, "Hello from ZIO!", Seq.empty)

  def main(args: Array[String]): Unit = {
    AsyncServerBuilder()
      .withZIORequestHandler(handler)
      .build()
  }
}
