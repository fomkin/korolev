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

  "flatMapAsync" should "merge streams" in {
    val seq1 = Seq(1, 2, 3)
    val seq2 = Seq(4, 5,6)
    val seq3 = Seq(7, 8, 9)
    Stream.apply(seq1, seq2, seq3)
      .mat()
      .flatMap { stream =>
        stream.flatMapAsync {
          chunk => Stream.emits(chunk).mat()
        }.fold(Seq.empty[Int]) { case (acc, x) => acc :+ x }
      }.map { result => assert(result == seq1 ++ seq2 ++ seq3) }
  }

  "flatMapMergeAsync" should "merge streams concurrently with factor 2" in {
    val seq1 = Seq(1, 2, 3)
    val seq2 = Seq(4, 5,6)
    val seq3 = Seq(7, 8, 9)
    Stream.apply(seq1, seq2, seq3)
      .mat()
      .flatMap { stream =>
        stream.flatMapMergeAsync(2){
          chunk => Stream.emits(chunk).mat()
        }.fold(Seq.empty[Int]) { case (acc, x) => acc :+ x }
      }.map { result =>
        assert(result.toSet == (seq1 ++ seq2 ++ seq3).toSet)
      }
  }

  "flatMapMergeAsync" should "merge streams concurrently with factor 3" in {
    val seq1 = Seq(1, 2, 3)
    val seq2 = Seq(4, 5,6)
    val seq3 = Seq(7, 8, 9)
    val seq4 = Seq(10, 11, 12)
    Stream.apply(seq1, seq2, seq3, seq4)
      .mat()
      .flatMap { stream =>
        stream.flatMapMergeAsync(3){
          chunk => Stream.emits(chunk).mat()
        }.fold(Seq.empty[Int]) { case (acc, x) => acc :+ x }
      }.map { result =>
       assert(result.toSet == (seq1 ++ seq2 ++ seq3 ++ seq4).toSet)
     }
  }

  "flatMapMergeAsync" should "merge streams with empty stream concurrently with factor 3" in {
    val seq1 = Seq(1, 2, 3)
    val seq2 = Seq(4, 5,6)
    val seq3 = Seq()
    val seq4 = Seq(10, 11, 12, 13, 14 ,15)
    Stream.apply(seq1, seq2, seq3, seq4)
      .mat()
      .flatMap { stream =>
        stream.flatMapMergeAsync(3){
          chunk => Stream.emits(chunk).mat()
        }.fold(Seq.empty[Int]) { case (acc, x) => acc :+ x }
      }.map { result =>
      assert(result.toSet == (seq1 ++ seq2 ++ seq3 ++ seq4).toSet)
    }
  }
}
