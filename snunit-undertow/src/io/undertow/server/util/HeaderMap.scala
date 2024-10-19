package io.undertow.util

import scala.collection
import scala.jdk.CollectionConverters._

final class HeaderMap private[undertow] (underlying: collection.Map[String, String])
    extends java.lang.Iterable[HeaderValues] {
  private lazy val underlyingMutable = underlying.asInstanceOf[scala.collection.mutable.Map[String, String]]

  def this() = this(collection.mutable.Map.empty[String, String])
  private[undertow] def toSNUnitHeaders: snunit.Headers =
    val headers = snunit.Headers(underlying.size)
    var i = 0
    underlying.foreach((k, v) =>
      headers.updateName(i, k)
      headers.updateValue(i, v)
      i += 1
    )
    headers

  def getFirst(headerName: String): String = underlying.getOrElse(headerName, null)
  def get(headerName: String): java.util.Collection[String] = {
    val l = new java.util.ArrayList[String]()
    l.add(underlying(headerName))
    l
  }
  def put(headerName: HttpString, headerValue: String): HeaderMap = {
    underlyingMutable(headerName.toString()) = headerValue
    this
  }
  def iterator(): java.util.Iterator[HeaderValues] = underlying.iterator.map { case (k, v) =>
    new HeaderValues(k, v)
  }.asJava
}
