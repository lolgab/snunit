package snunit

private [snunit] object EventPollingExecutorScheduler {
  private object GCRoots {
    private val references = new java.util.IdentityHashMap[AnyRef, Unit]
    def addRoot(o: AnyRef): Unit = references.put(o, ())
    def removeRoot(o: AnyRef): Unit = references.remove(o)
  }

  def monitor(fd: Int, reads: Boolean, writes: Boolean)(cb: EventNotificationCallback): Runnable = {
    val newCB: EventNotificationCallback = new EventNotificationCallback {
      def notifyEvents(readReady: Boolean, writeReady: Boolean): Unit = {
        GCRoots.removeRoot(unmonitorCallback)
        cb.notifyEvents(readReady = readReady, writeReady = writeReady)
      }
    }

    val innerStop = EventPollingExecutorSchedulerImpl.monitor(fd, reads, writes, newCB)

    val stop = new Runnable {
      def run(): Unit = {
        GCRoots.removeRoot(this)
        innerStop.run()
      }
    }

    newCB.unmonitorCallback = stop
    GCRoots.addRoot(stop)

    stop
  }

  trait EventNotificationCallback {
    private var _unmonitorCallback: Runnable = null
    private [EventPollingExecutorScheduler] def unmonitorCallback: Runnable = _unmonitorCallback
    private [EventPollingExecutorScheduler] def unmonitorCallback_=(runnable: Runnable): Unit = {
      _unmonitorCallback = runnable
    }

    def notifyEvents(readReady: Boolean, writeReady: Boolean): Unit
  }
}
