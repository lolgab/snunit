package snunit

trait Request {
  def method: Method
  def headers: Seq[(String, String)]
  def content: String = new String(contentRaw)
  def contentRaw: Array[Byte]
  def path: String
  def query: String
  def send(statusCode: StatusCode, content: Array[Byte], headers: Seq[(String, String)]): Unit
  def send(statusCode: StatusCode, content: String, headers: Seq[(String, String)]): Unit = {
    send(statusCode, content.getBytes(), headers)
  }
}
