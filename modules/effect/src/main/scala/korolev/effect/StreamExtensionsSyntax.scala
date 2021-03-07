package korolev.effect


object StreamExtensionsSyntax {

  implicit class KorolevUnchunkExtension[F[_]: Effect, A](stream: Stream[F, Seq[A]]) {

    /**
      * Flatten Stream of any collection to single elements
      * @return
      */
    def unchunk[O]: Stream[F, A] =
      stream.flatMapAsync(chunk => Stream.apply(chunk:_*).mat())

  }

}


