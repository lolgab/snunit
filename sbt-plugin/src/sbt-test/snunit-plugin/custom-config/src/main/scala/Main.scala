import snunit._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    SyncServerBuilder
      .build(_.send(StatusCode.OK, "Hello world", Seq()))
      .listen()
  }
}
