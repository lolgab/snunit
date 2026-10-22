package io.undertow

import _root_.io.undertow.server._
import snunit.*

class Undertow private (builder: Undertow.Builder) {
  private val handler: HttpHandler = builder.handler

  def start(): Unit = {
    SyncServerBuilder
      .setRequestHandler(req => handler.handleRequest(new HttpServerExchange(req)))
      .build()
      .listen()
  }

  /** Shutdown is handled by the launcher on SIGTERM/SIGINT/SIGHUP, so there is nothing to stop here. */
  def stop(): Unit = ()
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
