package snunit.tests

import snunit.*

object AsyncEpollcat {
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder
      .setRequestHandler(_.send(StatusCode.OK, "Hello world from epollcat!", Headers.empty))
      .build()
  }
}
