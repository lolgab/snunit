def zioHttpSources = T {
  val dest = T.dest
  os.proc("git", "clone", "--branch", "v2.0.0-RC6", "--depth", "1", "https://github.com/dream11/zio-http.git", dest).call()
  PathRef(dest)
}
