package snunit

trait SyncServer extends Server {
  def listen(): Unit
}
