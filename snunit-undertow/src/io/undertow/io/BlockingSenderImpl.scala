package io.undertow.io

import io.undertow.server.HttpServerExchange

import java.io.OutputStream
import java.nio.charset.StandardCharsets

class BlockingSenderImpl(exchange: HttpServerExchange, outputStream: OutputStream) extends Sender {
  def send(data: String): Unit = {
    outputStream.write(data.getBytes(StandardCharsets.UTF_8))
  }
}
