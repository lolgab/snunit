package io.undertow.io

trait Sender {
  def send(data: String): Unit
}
