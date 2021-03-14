package io.undertow.server.handlers

class CookieImpl(name: String, value: String) extends Cookie {
  override def getName(): String = ???
  override def getValue(): String = ???
  override def getComment(): String = ???
  override def getDomain(): String = ???
  override def getExpires(): java.util.Date = ???
  override def getMaxAge(): Integer = ???
  override def getPath(): String = ???
  override def getVersion(): Int = ???
  override def isDiscard(): Boolean = ???
  override def isHttpOnly(): Boolean = ???
  override def isSecure(): Boolean = ???
  override def getSameSiteMode(): String = ???

  override def setComment(comment: String): Cookie = ???
  override def setDomain(dommain: String): Cookie = ???
  override def setExpires(expires: java.util.Date): Cookie = ???
  override def setMaxAge(maxAge: Integer): Cookie = ???
  override def setPath(path: String): Cookie = ???
  override def setVersion(version: Int): Cookie = ???
  override def setDiscard(discard: Boolean): Cookie = ???
  override def setHttpOnly(httpOnly: Boolean): Cookie = ???
  override def setSecure(secure: Boolean): Cookie = ???
  override def setSameSiteMode(sameSite: String): Cookie = ???
}
