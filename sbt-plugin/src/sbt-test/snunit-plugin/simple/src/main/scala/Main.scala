import snunit._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    SyncServerBuilder
      .setRequestHandler(_.send(StatusCode.OK, "Hello world", Seq.empty[(String, String)]))
      .build()
      .listen()
  }
}
