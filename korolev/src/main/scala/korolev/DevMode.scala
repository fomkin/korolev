package korolev

import java.io.File

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
private[korolev] object DevMode {

  private val DevModeKey = "korolev.dev"
  private val DevModeDirectoryKey = "korolev.dev.directory"
  private val DevModeDefaultDirectory = "target/korolev/"

  val isActive = sys.env.get(DevModeKey)
    .orElse(sys.props.get(DevModeKey))
    .fold(false)(_ == "true")

  lazy val workDirectory = {
    val directoryPath = sys.env.get(DevModeDirectoryKey)
      .orElse(sys.props.get(DevModeDirectoryKey))
      .getOrElse(DevModeDefaultDirectory)

    val file = new File(directoryPath)
    if (!file.exists()) {
      file.mkdirs()
    } else if (!file.isDirectory) {
      throw new ExceptionInInitializerError(s"$directoryPath should be directory")
    }
    file
  }

  lazy val sessionsDirectory = {
    val file = new File(workDirectory, "sessions")
    file.mkdir()
    file
  }

  lazy val renderStateDirectory = {
    val f = new File(workDirectory, "render-states")
    f.mkdir()
    f
  }
}
