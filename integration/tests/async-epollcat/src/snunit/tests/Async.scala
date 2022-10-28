package snunit.tests

object AsyncEpollcat {
  def main(args: Array[String]): Unit = {
    snunit.AsyncServerBuilder.build(_.send(snunit.StatusCode.OK, "Hello world from epollcat!", Seq.empty))
  }
}
