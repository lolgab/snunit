package snunit

trait SyncServer extends Any with Server {
  def listen(): Unit
}
