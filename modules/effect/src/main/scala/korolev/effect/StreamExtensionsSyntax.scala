package korolev.effect


object StreamExtensionsSyntax {

  implicit class KorolevUnchunkExtension[F[_]: Effect, A](stream: Stream[F, Seq[A]]) {

    /**
      * Flatten Stream of any collection to single elements
      * @return
      */
    def unchunk[O]: Stream[F, A] = new Stream[F, A] {

      private var current: Seq[A] = Seq.empty[A]
      private var currentPos: Int      = 0
      private var currentChunkLen: Int = 0
      private var done: Boolean        = false

      @inline private def availableInCurrentChunk: Int = currentChunkLen - currentPos

      @inline
      private def readOne(): A = {
        val res = current(currentPos)
        currentPos += 1
        res
      }

      @inline def loadChunk(chunk: Seq[A]) = {
        current = chunk
        currentChunkLen = current.length
        currentPos = 0
      }

      def pull(): F[Option[A]] = {
        if(done) {
          Effect[F].pure(None)
        } else if (availableInCurrentChunk > 0) {
          Effect[F].pure(Some(readOne()))
        } else {
          Effect[F].flatMap(stream.pull()) {
            case Some(chunk) =>
              loadChunk(chunk)
              this.pull()
            case None =>
              done = true
              Effect[F].pure(None)
          }
        }
      }

      def cancel(): F[Unit] = stream.cancel()
    }

  }

}


