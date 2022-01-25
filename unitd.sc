private var optProc: Option[os.SubProcess] = None
private def close(): Unit = {
  optProc.foreach { proc =>
    proc.close()
    optProc = None
  }
}
Runtime.getRuntime().addShutdownHook(new Thread(close _))
def runBackground(dest: os.Path, config: ujson.Obj): Unit = {
  val state = dest / "state"
  os.makeDir(state)
  os.write(state / "conf.json", config)
  val control = dest / "control.sock"
  close()
  optProc = Some(
    os.proc("unitd", "--no-daemon", "--log", "/dev/stdout", "--state", state, "--control", s"unix:$control").spawn()
  )
}
