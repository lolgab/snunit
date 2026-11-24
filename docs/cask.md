---
title: Cask
---

`snunit-cask` is [Cask](https://com-lihaoyi.github.io/cask/) itself, compiled for Scala Native on top of
[`snunit-undertow`](undertow.html) with a [small patch](https://github.com/lolgab/snunit/blob/main/cask.patch).
You write a normal Cask application:

```scala
//> using dep com.github.lolgab::snunit-cask::{{ site.snunit_version }}

object MinimalApplication extends cask.MainRoutes {
  @cask.get("/")
  def hello() = "Hello World!"

  @cask.get("/hello")
  def hello(name: String) = s"Hello $name!"

  @cask.post("/do-thing")
  def doThing(request: cask.Request) = request.text().reverse

  initialize()
}
```

You can find an example [in tests](https://github.com/lolgab/snunit/blob/main/integration/tests/cask-helloworld/src/Main.scala).

## Differences from Cask on the JVM

The virtual thread executor of Cask 0.11 is disabled, because `java.lang.invoke` doesn't exist on
Scala Native. Requests always use the blocking handler.
