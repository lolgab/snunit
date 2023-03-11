package snunit.tests

import snunit._

object EmptyResponse {
  def main(args: Array[String]): Unit = {
    val server =
      SyncServerBuilder
        .setRequestHandler(
          _.send(
            statusCode = StatusCode.OK,
            content = Array.emptyByteArray,
            headers = Seq.empty
          )
        )
        .build()

    server.listen()
  }
}
