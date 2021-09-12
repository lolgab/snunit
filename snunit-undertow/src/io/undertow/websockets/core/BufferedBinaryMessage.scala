package io.undertow.websockets.core

import java.nio.ByteBuffer

import org.xnio.Pooled

class BufferedBinaryMessage {
  def getData(): Pooled[Array[ByteBuffer]] = ???
}
