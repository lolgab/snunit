private var optProc: Option[os.SubProcess] = None
private def close(): Unit = {
  optProc.foreach { proc =>
    proc.close()
    optProc = None
  }
}
Runtime.getRuntime().addShutdownHook(new Thread(close _))
def runBackground(config: ujson.Obj): Unit = {
  val dest = os.home / ".cache" / "snunit"
  val state = dest / "state"
  os.makeDir.all(state)
  os.write.over(state / "conf.json", config)
  val control = dest / "control.sock"
  os.remove(control)
  close()
  var started = new java.util.concurrent.atomic.AtomicBoolean(false)
  val spawned = os
    .proc(
      "unitd",
      "--no-daemon",
      "--log",
      "/dev/stdout",
      "--state",
      state,
      "--control",
      s"unix:$control",
      "--pid",
      dest / "unit.pid"
    )
    .spawn(stderr = os.ProcessOutput.Readlines(line => {
      line match {
        case s"$_ unit $_ started" =>
          started.set(true)
        case _ =>
      }
      System.err.println(line)
    }))
  while(!started.get()) {
    println("Waiting for unit to start...")
    Thread.sleep(100)
  }
  optProc = Some(spawned)
}
