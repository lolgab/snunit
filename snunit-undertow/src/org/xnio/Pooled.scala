package org.xnio

trait Pooled[T] {
  def getResource(): T
}
