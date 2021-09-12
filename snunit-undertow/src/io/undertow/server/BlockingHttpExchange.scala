package io.undertow.server

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

import io.undertow.io.Sender

trait BlockingHttpExchange extends Closeable {
  def getInputStream(): InputStream
  def getOutputStream(): OutputStream
  def getSender(): Sender
  def close(): Unit
}
