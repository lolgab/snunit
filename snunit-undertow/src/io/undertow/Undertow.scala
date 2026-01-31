package io.undertow

import _root_.io.undertow.server._
import snunit.*

class Undertow private (builder: Undertow.Builder) {
  private val handler: HttpHandler = builder.handler

  def start(): Unit = {
    SyncServerBuilder
      .setRequestHandler(req => handler.handleRequest(new HttpServerExchange(req)))
      .setHost(builder.host)
      .setPort(builder.port)
      .build()
      .listen()
  }
}

object Undertow {

  final class Builder private[Undertow] () {
    private[Undertow] var handler: HttpHandler = null
    private[Undertow] var host: String = null
    private[Undertow] var port: Int = -1

    def addHttpListener(port: Int, host: String): Builder =
      this.host = host
      this.port = port
      this

    def setHandler(handler: HttpHandler): Builder = {
      this.handler = handler
      this
    }
    def build(): Undertow = new Undertow(this)
  }
  def builder(): Builder = new Builder()
}
