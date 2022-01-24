package io.undertow.websockets.core

import org.xnio.Pooled

import java.nio.ByteBuffer

class BufferedBinaryMessage {
  def getData(): Pooled[Array[ByteBuffer]] = ???
}
