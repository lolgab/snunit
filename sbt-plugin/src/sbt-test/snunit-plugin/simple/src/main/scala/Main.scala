import snunit.*

object HelloWorld {
  def main(args: Array[String]): Unit = {
    SyncServerBuilder
      .setRequestHandler(_.send(StatusCode.OK, "Hello world", Headers.empty))
      .build()
      .listen()
  }
}
