package korolev.effect

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class StreamSpec extends AsyncFlatSpec with Matchers {

  "fold" should "accumulated all values left to right" in {
    Stream(1,2,3)
      .mat()
      .flatMap(_.fold(0) { case (acc, x) => acc + x })
      .map(result => assert(result == 6))
  }

  "concat" should "concatenate two streams" in {
    for {
      left <- Stream.apply(1,2,3).mat()
      right <- Stream.apply(4,5,6).mat()
      result <- (left ++ right).fold("") {
        case (acc, x) => acc + x
      }
    } yield assert(result == "123456")
  }
}
