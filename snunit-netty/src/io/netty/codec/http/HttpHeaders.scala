package io.netty.handler.codec.http

import java.{util => ju}

abstract class HttpHeaders extends java.lang.Iterable[ju.Map.Entry[String, String]] {
  def set(name: String, value: Object): HttpHeaders
  def iteratorCharSequence(): ju.Iterator[ju.Map.Entry[CharSequence, CharSequence]]
  def entries(): ju.List[ju.Map.Entry[String, String]]
}
