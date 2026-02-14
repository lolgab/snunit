package io.undertow.util

import scala.jdk.CollectionConverters._

final class HeaderValues private[undertow] (key: String, value: String)
    extends java.util.AbstractCollection[String]
    with java.util.Deque[String]
    with java.util.List[String] {
  def getHeaderName(): String = key
  def descendingIterator(): java.util.Iterator[String] = Array(value).iterator.asJava
  def element(): String = value

  // Deque methods (single-element: get/peek return value; add/remove throw)
  def addFirst(x$0: String): Unit = throw new UnsupportedOperationException
  def addLast(x$0: String): Unit = throw new UnsupportedOperationException
  def getFirst(): String = value
  def getLast(): String = value
  def removeFirst(): String = throw new UnsupportedOperationException
  def removeLast(): String = throw new UnsupportedOperationException

  // Members declared in java.util.List
  def add(x$1: Int, x$2: String): Unit = ???
  def addAll(x$1: Int, x$2: java.util.Collection[? <: String]): Boolean = ???
  def get(x$1: Int): String = ???
  def indexOf(x$1: Object): Int = ???
  def iterator(): java.util.Iterator[String] = Array(value).iterator.asJava
  def lastIndexOf(x$1: Object): Int = ???
  def listIterator(x$1: Int): java.util.ListIterator[String] = ???
  def listIterator(): java.util.ListIterator[String] = ???
  def remove(x$1: Int): String = ???
  def set(x$1: Int, x$2: String): String = ???
  def size(): Int = ???
  def subList(x$1: Int, x$2: Int): java.util.List[String] = ???
  def offer(x$0: String): Boolean = ???
  def offerFirst(x$0: String): Boolean = ???
  def offerLast(x$0: String): Boolean = ???
  def peek(): String = ???
  def peekFirst(): String = ???
  def peekLast(): String = ???
  def poll(): String = ???
  def pollFirst(): String = ???
  def pollLast(): String = ???
  def pop(): String = ???
  def push(x$0: String): Unit = ???
  def remove(): String = ???
  def removeFirstOccurrence(x$0: Object): Boolean = ???
  def removeLastOccurrence(x$0: Object): Boolean = ???
  def reversed(): HeaderValues = this
}
