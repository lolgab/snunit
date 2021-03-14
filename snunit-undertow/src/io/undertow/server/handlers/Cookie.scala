package io.undertow.server.handlers

trait Cookie {
  def getName(): String
  def getValue(): String
  def getComment(): String
  def getDomain(): String
  def getExpires(): java.util.Date
  def getMaxAge(): Integer
  def getPath(): String
  def getVersion(): Int
  def isDiscard(): Boolean
  def isHttpOnly(): Boolean
  def isSecure(): Boolean
  def getSameSiteMode(): String

  def setComment(comment: String): Cookie
  def setDomain(dommain: String): Cookie
  def setExpires(expires: java.util.Date): Cookie
  def setMaxAge(maxAge: Integer): Cookie
  def setPath(path: String): Cookie
  def setVersion(version: Int): Cookie
  def setDiscard(discard: Boolean): Cookie
  def setHttpOnly(httpOnly: Boolean): Cookie
  def setSecure(secure: Boolean): Cookie
  def setSameSiteMode(sameSite: String): Cookie
}
