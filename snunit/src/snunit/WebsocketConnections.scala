package snunit

/** Keeps track of the open websocket connections and dispatches the incoming frames to them.
  *
  * snunit has a single global [[WebsocketHandler]], so integrations register their connections here by request id.
  */
object WebsocketConnections extends WebsocketHandler {
  private val connections = new java.util.HashMap[Long, (Byte, Boolean, Array[Byte]) => Unit]

  /** Registers a connection which was already upgraded. `onFrame` receives the opcode, the fin flag and the content. */
  def register(req: Request, onFrame: (Byte, Boolean, Array[Byte]) => Unit): Unit =
    connections.put(req.id, onFrame)

  /** Forgets the connection and finishes the request */
  def unregister(req: Request): Unit = {
    connections.remove(req.id)
    req.sendDone()
  }

  override def handleFrame(frame: Frame): Unit = {
    val req = frame.frameRequest
    val opcode = frame.opcode
    val last = frame.fin != 0
    val bytes = frame.frameContentRaw()
    frame.sendFrameDone()
    val onFrame = connections.get(req.id)
    if (onFrame != null) onFrame(opcode, last, bytes)
    else if (opcode == Opcode.Close.value) req.sendDone()
  }
}
