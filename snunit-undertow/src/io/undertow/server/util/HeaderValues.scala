package io.undertow.util

import scala.jdk.CollectionConverters._

final class HeaderValues private[undertow] (key: String, value: String)
    extends java.util.AbstractCollection[String]
    with java.util.Deque[String]
    with java.util.List[String] {
  private def unsupported(): Nothing =
    throw new UnsupportedOperationException("HeaderValues is immutable")

  private def nonEmpty: Boolean = value != null

  private def requireNonEmpty(): Unit =
    if !nonEmpty then throw new java.util.NoSuchElementException()

  def getHeaderName(): String = key
  override def addFirst(x$1: String): Unit = unsupported()
  override def addLast(x$1: String): Unit = unsupported()
  override def descendingIterator(): java.util.Iterator[String] = iterator()
  override def element(): String =
    requireNonEmpty()
    value
  override def getFirst(): String = element()
  override def getLast(): String = element()
  override def offer(x$1: String): Boolean = unsupported()
  override def offerFirst(x$1: String): Boolean = unsupported()
  override def offerLast(x$1: String): Boolean = unsupported()
  override def peek(): String = if nonEmpty then value else null
  override def peekFirst(): String = peek()
  override def peekLast(): String = peek()
  override def poll(): String = unsupported()
  override def pollFirst(): String = unsupported()
  override def pollLast(): String = unsupported()
  override def pop(): String = unsupported()
  override def push(x$1: String): Unit = unsupported()
  override def remove(): String = unsupported()
  override def removeFirst(): String = unsupported()
  override def removeFirstOccurrence(x$1: Object): Boolean = unsupported()
  override def removeLast(): String = unsupported()
  override def removeLastOccurrence(x$1: Object): Boolean = unsupported()
  override def reversed(): java.util.List[String] & java.util.Deque[String] = this

  // Members declared in java.util.List
  override def add(x$1: Int, x$2: String): Unit = unsupported()
  override def addAll(x$1: Int, x$2: java.util.Collection[? <: String]): Boolean = unsupported()
  override def get(x$1: Int): String =
    if x$1 == 0 && nonEmpty then value
    else throw new IndexOutOfBoundsException(s"Index: ${x$1}, Size: ${size()}")
  override def indexOf(x$1: Object): Int = if nonEmpty && value == x$1 then 0 else -1
  override def iterator(): java.util.Iterator[String] =
    (if nonEmpty then Array(value) else Array.empty[String]).iterator.asJava
  override def lastIndexOf(x$1: Object): Int = indexOf(x$1)
  override def listIterator(x$1: Int): java.util.ListIterator[String] = {
    val list =
      if nonEmpty then java.util.Collections.singletonList(value) else java.util.Collections.emptyList[String]()
    list.listIterator(x$1)
  }
  override def listIterator(): java.util.ListIterator[String] = listIterator(0)
  override def remove(x$1: Int): String = unsupported()
  override def set(x$1: Int, x$2: String): String = unsupported()
  override def size(): Int = if nonEmpty then 1 else 0
  override def subList(x$1: Int, x$2: Int): java.util.List[String] = {
    val list =
      if nonEmpty then java.util.Collections.singletonList(value) else java.util.Collections.emptyList[String]()
    list.subList(x$1, x$2)
  }
}
