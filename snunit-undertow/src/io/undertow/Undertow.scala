package io.undertow

import _root_.io.undertow.server._
import snunit._

class Undertow private (builder: Undertow.Builder) {
  private val handler: HttpHandler = builder.handler

  def start(): Unit = {
    SyncServerBuilder
      .setRequestHandler(req => handler.handleRequest(new HttpServerExchange(req)))
      .build()
      .listen()
  }
}

object Undertow {

  final class Builder private[Undertow] () {
    private[Undertow] var handler: HttpHandler = null

    def addHttpListener(port: Int, host: String): Builder = this
    def setHandler(handler: HttpHandler): Builder = {
      this.handler = handler
      this
    }
    def build(): Undertow = new Undertow(this)
  }
  def builder(): Builder = new Builder()
}
