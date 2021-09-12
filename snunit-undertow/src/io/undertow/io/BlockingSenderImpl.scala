package io.undertow.io

import java.io.OutputStream
import java.nio.charset.StandardCharsets

import io.undertow.server.HttpServerExchange

class BlockingSenderImpl(exchange: HttpServerExchange, outputStream: OutputStream) extends Sender {
  def send(data: String): Unit = {
    outputStream.write(data.getBytes(StandardCharsets.UTF_8))
  }
}
