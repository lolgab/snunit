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

  override def setComment(comment: String): CookieImpl = ???
  override def setDomain(dommain: String): CookieImpl = ???
  override def setExpires(expires: java.util.Date): CookieImpl = ???
  override def setMaxAge(maxAge: Integer): CookieImpl = ???
  override def setPath(path: String): CookieImpl = ???
  override def setVersion(version: Int): CookieImpl = ???
  override def setDiscard(discard: Boolean): CookieImpl = ???
  override def setHttpOnly(httpOnly: Boolean): CookieImpl = ???
  override def setSecure(secure: Boolean): CookieImpl = ???
  override def setSameSiteMode(sameSite: String): CookieImpl = ???
}
