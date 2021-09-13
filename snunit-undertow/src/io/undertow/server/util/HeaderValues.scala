package io.undertow.util

import scala.jdk.CollectionConverters._

final class HeaderValues private[undertow] (key: String, value: String)
    extends java.util.AbstractCollection[String]
    with java.util.Deque[String]
    with java.util.List[String] {
  def getHeaderName(): String = key
  def addFirst(x$1: String): Unit = ???
  def addLast(x$1: String): Unit = ???
  def descendingIterator(): java.util.Iterator[String] = ???
  def element(): String = ???
  def getFirst(): String = ???
  def getLast(): String = ???
  def offer(x$1: String): Boolean = ???
  def offerFirst(x$1: String): Boolean = ???
  def offerLast(x$1: String): Boolean = ???
  def peek(): String = ???
  def peekFirst(): String = ???
  def peekLast(): String = ???
  def poll(): String = ???
  def pollFirst(): String = ???
  def pollLast(): String = ???
  def pop(): String = ???
  def push(x$1: String): Unit = ???
  def remove(): String = ???
  def removeFirst(): String = ???
  def removeFirstOccurrence(x$1: Object): Boolean = ???
  def removeLast(): String = ???
  def removeLastOccurrence(x$1: Object): Boolean = ???

  // Members declared in java.util.List
  def add(x$1: Int, x$2: String): Unit = ???
  def addAll(x$1: Int, x$2: java.util.Collection[_ <: String]): Boolean = ???
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
}
