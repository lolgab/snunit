package snunit.tests

import endpoints4s.algebra

trait HelloWorldEndpoints extends algebra.Endpoints {
  val helloWorld = endpoint(get(path / "hello" /? qs[String]("name")), ok(textResponse))
}

object Endpoints4sHelloWorld extends snunit.endpoints4s.Endpoints with HelloWorldEndpoints {
  def main(args: Array[String]): Unit =
    snunit.SyncServerBuilder
      .setRequestHandler(toHandler(helloWorld.implementedBy(name => s"Hello $name!")))
      .build()
      .listen()
}
