package snunit.snunitzio

class ZIORequest private[snunitzio] (private val underlying: _root_.snunit.Request) {
  def content = underlying.content
  def contentRaw = underlying.contentRaw
  def path = underlying.path
  def method = underlying.method
  def headers = underlying.headers
}
