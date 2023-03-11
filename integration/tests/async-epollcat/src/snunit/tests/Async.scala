package snunit.tests

object AsyncEpollcat {
  def main(args: Array[String]): Unit = {
    snunit.AsyncServerBuilder
      .setRequestHandler(_.send(snunit.StatusCode.OK, "Hello world from epollcat!", Seq.empty))
      .build()
  }
}
