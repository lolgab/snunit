package io.undertow.server

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

trait BlockingHttpExchange extends Closeable {
  def getInputStream(): InputStream
  def getOutputStream(): OutputStream
  def close(): Unit
}
