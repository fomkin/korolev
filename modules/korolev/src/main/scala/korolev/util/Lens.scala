package korolev.util

/**
 * Very simple all-in-one optic.
 */
case class Lens[S, S2](read: PartialFunction[S, S2],
                       write: PartialFunction[(S, S2), S]) {

  def get(that: S): Option[S2] =
    read.lift(that)

  def modify(that: S)(f: S2 => S2): S =
    read.lift(that).fold(that) { s2 =>
      write(that, f(s2))
    }

  def update(that: S, value: S2): S =
    write.lift(that, value).getOrElse(that)

  def focus[S3](read: PartialFunction[S2, S3],
                write: PartialFunction[(S2, S3), S2]): Lens[S, S3] = {
    val composedWrite: (S, S3) => Option[S] = { (s, s3) =>
      for {
        origS2 <- this.read.lift(s)
        updatedS2 <- write.lift(origS2, s3)
        updatedS <- this.write.lift(s, updatedS2)
      } yield updatedS
    }
    Lens(this.read.andThen(read), Function.unlift(composedWrite.tupled))
  }

  def ++[S3](lens: Lens[S2, S3]): Lens[S, S3] = focus(lens.read, lens.write)
}
