/*
 * Copyright 2017-2020 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.internal

import java.io.{File, RandomAccessFile}
import java.nio.ByteBuffer

import levsha.impl.DiffRenderContext

private[korolev] object DevMode {

  private val DevModeKey = "korolev.dev"
  private val DevModeDirectoryKey = "korolev.dev.directory"
  private val DevModeDefaultDirectory = "target/korolev/"

  class ForRenderContext(identifier: String) {

    lazy val file = new File(DevMode.renderStateDirectory, identifier)

    lazy val saved: Boolean =
      DevMode.isActive && file.exists

    def isActive = DevMode.isActive

    def loadRenderContext() =
      if (saved) {
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

    def saveRenderContext(renderContext: DiffRenderContext[_]): Unit = {
      val nioFile = new RandomAccessFile(file, "rw")
      val channel = nioFile.getChannel
      try {
        val buffer = renderContext.save()
        channel.write(buffer)
        ()
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
