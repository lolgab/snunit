package snunit.tests

import snunit._

object EmptyResponse {
  def main(args: Array[String]): Unit = {
    val server =
      SyncServerBuilder()
        .withRequestHandler(
          _.send(
            statusCode = StatusCode.OK,
            content = Array[Byte](),
            headers = Seq.empty
          )
        )
        .build()

    server.listen()
  }
}
