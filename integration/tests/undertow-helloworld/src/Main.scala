package snunit.tests

import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers

object HelloWorldServer {

  def main(args: Array[String]): Unit = {
    val server: Undertow = Undertow
      .builder()
      .addHttpListener(8080, "localhost")
      .setHandler(new HttpHandler() {
        def handleRequest(exchange: HttpServerExchange) = {
          exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
          exchange.getResponseSender().send("Hello World");
        }
      })
      .build()
    server.start()
  }
}
