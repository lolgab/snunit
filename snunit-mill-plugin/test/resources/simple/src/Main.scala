import snunit.*

object HelloWorld {
  def main(args: Array[String]): Unit = {
    SyncServerBuilder
      .setRequestHandler(_.send(StatusCode.OK, "TEST SNUnit Mill Plugin", Headers.empty))
      .build()
      .listen()
  }
}
