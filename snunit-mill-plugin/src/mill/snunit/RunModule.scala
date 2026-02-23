package mill.snunitinternal

object RunModule {

  def backgroundSetup(dest: os.Path): (os.Path, os.Path, String) = {
    mill.scalalib.RunModule.backgroundSetup(dest)
  }

}
