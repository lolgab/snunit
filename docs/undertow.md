---
title: Undertow
---

`snunit-undertow` implements the [Undertow](https://undertow.io) API (`Undertow.builder()`,
`HttpHandler`, `HttpServerExchange`...) with SNUnit. [Cask](cask.html) is built on it.

It is not the whole Undertow API, but code written against the builder and `HttpHandler` works:

```scala
//> using dep com.github.lolgab::snunit-undertow::{{ site.snunit_version }}
import io.undertow.Undertow
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.Headers

object HelloWorldServer {
  def main(args: Array[String]): Unit =
    Undertow
      .builder()
      .addHttpListener(8080, "localhost")
      .setHandler(new HttpHandler {
        def handleRequest(exchange: HttpServerExchange) = {
          exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/plain")
          exchange.getResponseSender.send("Hello World")
        }
      })
      .build()
      .start()
}
```

You can find an example [in tests](https://github.com/lolgab/snunit/blob/main/integration/tests/undertow-helloworld/src/Main.scala).
