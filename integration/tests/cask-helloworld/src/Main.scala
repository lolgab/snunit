package snunit.tests

import io.undertow.Undertow

object MinimalApplication extends cask.MainRoutes {
  @cask.get("/")
  def hello() = {
    "Hello World!"
  }

  @cask.get("/hello")
  def hello(name: String) = {
    s"Hello $name!"
  }

  @cask.post("/do-thing")
  def doThing(request: cask.Request) = {
    request.text().reverse
  }

  override def main(args: Array[String]): Unit = {
    val server = Undertow
      .builder()
      .addHttpListener(port, host)
      .setHandler(defaultHandler)
      .build()
    server.start()
  }

  initialize()

}
