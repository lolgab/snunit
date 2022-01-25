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
  os.write(state / "conf.json", config)
  val control = dest / "control.sock"
  close()
  optProc = Some(
    os.proc(
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
    ).spawn()
  )
}
