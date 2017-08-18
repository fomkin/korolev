package korolev.internal

import java.io.{File, RandomAccessFile}
import java.nio.ByteBuffer

import levsha.impl.DiffRenderContext

private[korolev] object DevMode {

  private val DevModeKey = "korolev.dev"
  private val DevModeDirectoryKey = "korolev.dev.directory"
  private val DevModeDefaultDirectory = "target/korolev/"

  class ForRenderContext(identifier: String, fromScratch: Boolean) {

    lazy val file = new File(DevMode.renderStateDirectory, identifier)

    lazy val hasSavedRenderContext = DevMode.isActive && file.exists && !fromScratch

    def isActive = DevMode.isActive

    def loadRenderContext() =
      if (hasSavedRenderContext) {
        val nioFile = new RandomAccessFile(file, "r")
        val channel = nioFile.getChannel
        try {
          val buffer = ByteBuffer.allocate(channel.size.toInt)
          channel.read(buffer)
          buffer.position(0)
          Some(buffer)
        } finally {
          nioFile.close()
          channel.close()
        }
      } else {
        None
      }

    def saveRenderContext(renderContext: DiffRenderContext[_]) = {
      val nioFile = new RandomAccessFile(file, "rw")
      val channel = nioFile.getChannel
      try {
        val buffer = renderContext.save()
        channel.write(buffer)
      } finally {
        nioFile.close()
        channel.close()
      }
    }
  }

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
    val file = new File(workDirectory, "render-contexts")
    file.mkdir()
    file
  }
}
