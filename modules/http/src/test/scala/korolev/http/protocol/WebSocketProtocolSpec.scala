package korolev.http.protocol

import korolev.data.ByteVector
import korolev.effect.Decoder
import korolev.http.protocol.WebSocketProtocol._
import korolev.web.Path.Root
import korolev.web.{Request, Response}
import org.scalatest.{Assertion, FlatSpec, Matchers}

import scala.annotation.tailrec
import scala.util.Random

class WebSocketProtocolSpec extends FlatSpec with Matchers {

  final val SliceTestFramesNumber = 10

  // Example handshake from RFC
  final val HandshakeKey = "dGhlIHNhbXBsZSBub25jZQ=="
  final val HandshakeAccept = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="
  final val HandshakeRequest = Request(
    method = Request.Method.Get,
    path = Root,
    headers = Seq(
      "connection" -> "upgrade",
      "sec-websocket-key" -> HandshakeKey,
      "sec-websocket-version" -> "13"
    ),
    contentLength = None,
    body = ()
  )
  final val BasicHttpRequest = Request(
    method = Request.Method.Get,
    path = Root,
    headers = Seq.empty,
    contentLength = None,
    body = ()
  )
  final val BasicHttpResponse = Response(
    status = Response.Status.Ok,
    headers = Nil,
    body = (),
    contentLength = Some(0L)
  )

  // Example frames from RFC
  final val helloUnmaskedBytes = ByteVector(0x81, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f)
  final val helloMaskedBytes = ByteVector(0x81, 0x85, 0x37, 0xfa, 0x21, 0x3d, 0x7f, 0x9f, 0x4d, 0x51, 0x58)
  final val helloFrame = Frame.Text(ByteVector.utf8("Hello"), fin = true)
  final val helloMask = 0x37fa213d

  "encodeFrame" should "produce same bytes as in RFC (https://tools.ietf.org/html/rfc6455) 5.7. unmasked example" in {
    encodeFrame(helloFrame, None) shouldEqual helloUnmaskedBytes
  }

  it should "produce same bytes as in masked example" in {
    encodeFrame(helloFrame, Some(helloMask)) shouldEqual helloMaskedBytes
  }

  "decodeFrames" should "process example frame (from RFC https://tools.ietf.org/html/rfc6455 5.7)" in {
    decodeFrame(helloUnmaskedBytes) should matchPattern {
      case (_, Decoder.Action.Push(`helloFrame`)) => ()
    }
  }

  it should "process example frame with mask" in {
    decodeFrame(helloMaskedBytes) should matchPattern {
      case (_, Decoder.Action.Push(`helloFrame`)) => ()
    }
  }

  it should "process two frames sequentially" in {
    val bytes = helloMaskedBytes ++ helloUnmaskedBytes
    val (_, Decoder.Action.Fork(frame1, rest)) = decodeFrame(bytes)
    val (_, Decoder.Action.Push(frame2)) = decodeFrame(rest)

    frame1 shouldEqual helloFrame
    frame2 shouldEqual helloFrame
  }

  it should "work properly when frames are sliced" in {
    val random = new Random(3)
    @tailrec def slice(acc: Vector[ByteVector], bytes: ByteVector, times: Int): Vector[ByteVector] =
      if (times == 0) acc :+ bytes else {
        val len = random.nextInt(bytes.length.toInt / 3).toLong + 1
        val lhs = bytes.slice(0, len)
        val rhs = bytes.slice(len)
        slice(acc :+ lhs, rhs, times - 1)
      }
    val frames = Vector.fill(SliceTestFramesNumber)(randomFrame(random, random.nextInt(15)))
    val encodedFrames = frames.foldLeft(ByteVector.empty) { (acc, frame) =>
      acc ++ encodeFrame(frame, None)
    }
    val slices = slice(Vector.empty, encodedFrames, (SliceTestFramesNumber * 1.5).toInt)
    val fsmInitial = (ByteVector.empty, DecodingState.Begin: DecodingState, Vector.empty[Frame])

    @tailrec
    def decodeSlice(buffer: ByteVector,
                    state: DecodingState,
                    acc: Vector[Frame],
                    slice: ByteVector): (ByteVector, DecodingState, Vector[Frame]) =
      decodeFrames(buffer, state, slice) match {
        case ((newBuffer, newState), Decoder.Action.Push(frame)) =>
          (newBuffer, newState, acc :+ frame)
        case ((newBuffer, newState), Decoder.Action.Fork(frame, restOfBytes)) =>
          decodeSlice(newBuffer, newState, acc :+ frame, restOfBytes)
        case ((newBuffer, newState), Decoder.Action.TakeNext) =>
          (newBuffer, newState, acc)
        case _ => throw new Exception("Unexpected action")
      }

    // Decode slices
    val (_, _, decodedFrames) = slices.foldLeft(fsmInitial) {
      case ((buffer, state, acc), slice) =>
        decodeSlice(buffer, state, acc, slice)
    }

    decodedFrames shouldEqual frames
  }

  "encode/decode" should "be isomorphic on small frame" in isoCheck(50, masked = false)

  it should "be isomorphic on mid frame" in isoCheck(1000, masked = false)

  it should "be isomorphic on mid frame #2" in isoCheck(43738, masked = false)

  it should "be isomorphic on large frame" in isoCheck(70000, masked = false)

  it should "be isomorphic on small frame with mask" in isoCheck(50, masked = true)

  it should "be isomorphic on mid frame with mask" in isoCheck(1000, masked = true)

  it should "be isomorphic on large frame with mask" in isoCheck(70000, masked = true)

  "findIntention" should "detect browser want to open WebSocket connection" in {
    val maybeIntention = findIntention(HandshakeRequest)
    maybeIntention shouldEqual Some(Intention(HandshakeKey))
  }

  it should "understand that browser send just a regular HTTP request" in {
    val maybeIntention = findIntention(BasicHttpRequest)
    maybeIntention shouldEqual None
  }

  "handshake" should "add right headers (example from RFC (from RFC https://tools.ietf.org/html/rfc6455 1.2)" in {
    val upgrade = handshake(BasicHttpResponse, Intention(HandshakeKey))
    upgrade.status.code shouldEqual 101
    upgrade.header("Upgrade") shouldEqual Some("websocket")
    upgrade.header("Connection") shouldEqual Some("Upgrade")
    upgrade.header("Sec-WebSocket-Accept") shouldEqual Some(HandshakeAccept)
  }

  private def randomFrame(random: Random, size: Int): Frame = {
    val data = new Array[Byte](size)
    random.nextBytes(data)
    Frame.Binary(ByteVector(data), fin = true)
  }

  /**
   * decode(encode(frame)) == frame
   */
  private def isoCheck(size: Int, masked: Boolean): Assertion = {
    val random = new Random(0)
    val frame = randomFrame(random, size)
    val mask = if (masked) Some(random.nextInt()) else None
    val bytes = encodeFrame(frame, mask)
    decodeFrame(bytes) should matchPattern {
      case (_, Decoder.Action.Push(`frame`)) => ()
    }
  }
}
