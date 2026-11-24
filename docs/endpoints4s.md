---
title: endpoints4s
---

The `snunit-endpoints4s` artifact offers a synchronous [endpoints4s](https://github.com/endpoints4s/endpoints4s)
server interpreter. Mix `snunit.endpoints4s.Endpoints` with your endpoint definitions and
pass `toHandler(endpoint.implementedBy(...))` to `SyncServerBuilder.setRequestHandler`:

```scala
//> using dep com.github.lolgab::snunit-endpoints4s::{{ site.snunit_version }}
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
```

You can find an example [in tests](https://github.com/lolgab/snunit/blob/main/integration/tests/endpoints4s-helloworld/src/Main.scala).
