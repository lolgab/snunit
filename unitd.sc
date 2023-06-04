import java.util.concurrent.atomic.AtomicBoolean

private val dest = os.home / ".cache" / "snunit"
val control = dest / "control.sock"
private val pid = dest / "unit.pid"

// Variables are not maintained among invocations in interactive (`-i`) mode.
// Running Mill with `-i` in TestUtils breaks makes it impossible to kill the previous processes.
private var optProc: Option[os.SubProcess] = None
private def closeUnitd(): Unit = {
  optProc.foreach { proc =>
    proc.close()
    // Wait for Unit to close itself gracefully
    Thread.sleep(100)
    optProc = None
  }
  // We also try to kill the process in the pid file
  if (os.exists(pid)) {
    os.proc("kill", os.read(pid).trim).call(check = false)
  }
}
def runBackground(config: ujson.Obj): Unit = {
  closeUnitd()
  val statedir = dest / "statedir"
  os.makeDir.all(statedir)
  os.write.over(statedir / "conf.json", config)
  os.remove(control)
  val started = new AtomicBoolean(false)
  optProc = Some(
    os.proc(
      "unitd",
      "--no-daemon",
      "--log",
      dest / "log.txt",
      "--statedir",
      statedir,
      "--control",
      s"unix:$control",
      "--pid",
      pid
    ).spawn(
      stdout = os.Inherit,
      stderr = os.ProcessOutput.Readlines(line => {
        line match {
          case s"$_ unit $_ started" =>
            Thread.sleep(100)
            started.set(true)
          case _ =>
        }
        System.err.println(line)
      })
    )
  )
  while (!started.get()) {
    println("Waiting for unit to start...")
    Thread.sleep(100)
  }
}
