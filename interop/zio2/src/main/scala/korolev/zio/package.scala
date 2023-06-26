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

package korolev

import korolev.effect.Effect
import _root_.zio.*
import korolev.data.BytesLike

import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

package object zio {

  /**
    * Provides [[Effect]] instance for ZIO[Any, Throwable, *].
    * Use this method if your app uses [[Throwable]] to express errors.
    */
  def taskEffectInstance[R](runtime: Runtime[R]): Effect[Task] =
    new Zio2Effect[Any, Throwable](runtime, identity, identity)

  final val taskEffectLayer: ULayer[Effect[Task]] =
    ZLayer(ZIO.runtime.map(taskEffectInstance))

  /**
    * Provides [[Effect]] instance for ZIO with arbitrary runtime
    * and error types. Korolev uses Throwable inside itself.
    * That means if you want to work with your own [[E]],
    * you should provide functions to convert [[Throwable]]
    * to [[E]] and vice versa.
    *
    * {{{
    *   sealed trait MyError
    *   object MyError {
    *     case class UserNotFound(id: Long) extends MyError
    *     case object DoNotLikeIt extends MyError
    *     case class Unexpected(e: Throwable) extends MyError
    *   }
    *   case class MyErrorException(error: MyError) extends Throwable
    *
    *   val runtime = new DefaultRuntime {}
    *   implicit val zioEffect = korolev.zio.zioEffectInstance(runtime)(MyError.Unexpected)(MyErrorException)
    *
    *   val ctx = Context[IO[MyError, *], MyState, Any]
    * }}}
    */
  final def zioEffectInstance[R, E](runtime: Runtime[R])
                                   (liftError: Throwable => E)
                                   (unliftError: E => Throwable): Effect[ZIO[R, E, *]] =
    new Zio2Effect[R, E](runtime, liftError, unliftError)

  implicit object ChunkBytesLike extends BytesLike[Chunk[Byte]] {
    override def empty: Chunk[Byte] = Chunk.empty[Byte]

    override def ascii(s: String): Chunk[Byte] = {
      val buffer = StandardCharsets.US_ASCII.encode(s)
      buffer.flip()
      Chunk.fromByteBuffer(buffer)
    }

    override def utf8(s: String): Chunk[Byte] = {
      val buffer = StandardCharsets.UTF_8.encode(s)
      buffer.flip()
      Chunk.fromByteBuffer(buffer)
    }

    override def wrapArray(bytes: Array[Byte]): Chunk[Byte] = Chunk.fromArray(bytes)

    override def copyBuffer(buffer: ByteBuffer): Chunk[Byte] = Chunk.fromByteBuffer(buffer)

    override def copyToBuffer(b: Chunk[Byte], buffer: ByteBuffer): Int = {
      val c = Math.min(b.length, buffer.remaining())
      b.foreach { x =>
        buffer.put(x)
      }
      c
    }

    override def copyFromArray(bytes: Array[Byte]): Chunk[Byte] = Chunk.fromArray(bytes)

    override def copyFromArray(bytes: Array[Byte], offset: Int, size: Int): Chunk[Byte] = {
      val builder = Chunk.newBuilder[Byte]
      for (i <- offset until size) {
        builder += (bytes(i))
      }
      builder.result()
    }

    override def copyToArray(value: Chunk[Byte], array: Array[Byte], sourceOffset: Int, targetOffset: Int, length: Int): Unit = {
      // TODO optimize me
      for (i <- sourceOffset until length) {
        array(targetOffset + i) = value(i)
      }
    }

    override def asAsciiString(bytes: Chunk[Byte]): String =
      StandardCharsets.US_ASCII.decode(asBuffer(bytes)).toString

    override def asUtf8String(bytes: Chunk[Byte]): String =
      StandardCharsets.UTF_8.decode(asBuffer(bytes)).toString

    override def asString(bytes: Chunk[Byte], charset: Charset): String =
      charset.decode(asBuffer(bytes)).toString

    override def asArray(bytes: Chunk[Byte]): Array[Byte] =
      bytes.toArray

    override def asBuffer(bytes: Chunk[Byte]): ByteBuffer = {
      val buff = ByteBuffer.allocate(bytes.length)
      copyToBuffer(bytes, buff)
      buff.flip()
      buff
    }

    override def eq(l: Chunk[Byte], r: Chunk[Byte]): Boolean =
      l.equals(r)

    override def get(bytes: Chunk[Byte], i: Long): Byte =
      bytes(i.toInt)

    override def length(bytes: Chunk[Byte]): Long =
      bytes.length.toLong

    override def concat(left: Chunk[Byte], right: Chunk[Byte]): Chunk[Byte] =
      left ++ right

    override def slice(bytes: Chunk[Byte], start: Long, end: Long): Chunk[Byte] =
      bytes.slice(start.toInt, end.toInt)

    override def mapWithIndex(bytes: Chunk[Byte], f: (Byte, Long) => Byte): Chunk[Byte] = {
      val builder = Chunk.newBuilder[Byte]
      var i = 0L
      bytes.foreach { x =>
        val res = f(x, i)
        builder += (res)
        i += 1
      }
      builder.result()
    }

    override def foreach(bytes: Chunk[Byte], f: Byte => Unit): Unit =
      bytes.foreach(f)
    override def indexOf(where: Chunk[Byte], that: Byte): Long =
      where.indexOf(that)

    override def indexOf(where: Chunk[Byte], that: Byte, from: Long): Long =
      where.indexOf(that, from.toInt)

    override def lastIndexOf(where: Chunk[Byte], that: Byte): Long =
      where.lastIndexOf(that)

    override def indexOfSlice(where: Chunk[Byte], that: Chunk[Byte]): Long =
      where.indexOfSlice(that)

    override def lastIndexOfSlice(where: Chunk[Byte], that: Chunk[Byte]): Long =
      where.lastIndexOfSlice(that)
  }
}
