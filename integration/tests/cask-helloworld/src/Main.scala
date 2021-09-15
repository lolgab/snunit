package snunit.tests

import io.undertow.Undertow

object MinimalApplication extends cask.MainRoutes {
  @cask.get("/")
  def hello() = {
    "Hello World!"
  }

  @cask.post("/do-thing")
  def doThing(request: cask.Request) = {
    request.text().reverse
  }

  @cask.get("/user/:userName")
  def showUserProfile(userName: String) = {
    s"User $userName"
  }

  @cask.get("/post/:postId")
  def showPost(postId: Int, param: Seq[String]) = {
    s"Post $postId $param"
  }

  @cask.get("/path", subpath = true)
  def showSubpath(request: cask.Request) = {
    s"Subpath ${request.remainingPathSegments}"
  }

  override def main(args: Array[String]): Unit = {
    val server = Undertow.builder
      .addHttpListener(port, host)
      .setHandler(defaultHandler)
      .build
    server.start()
  }

  initialize()

}
