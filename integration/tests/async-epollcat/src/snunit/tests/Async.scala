package snunit.tests

import snunit._

object AsyncEpollcat {
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder
      .setRequestHandler(_.send(StatusCode.OK, "Hello world from epollcat!", Seq.empty[(String, String)]))
      .build()
  }
}
