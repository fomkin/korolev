package korolev.http.protocol

import korolev.data.Bytes
import korolev.effect.{Decoder, Stream}
import korolev.web.{PathAndQuery, Request, Response}
import org.scalacheck._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class Http11Spec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  val http11 = new Http11[Bytes]

  // TODO use from effect
  implicit val ec: ExecutionContext = new  ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }

  "decodeLimitedBody" should "pass bytes if content length not reached and byteTotal is zero" in {
    val bytes = Bytes(1, 2, 3, 4, 5)
    val contentLength = 10L
    val (bytesTotal, frame) = http11.decodeLimitedBody(0, bytes, contentLength)
    bytesTotal shouldEqual 5
    frame shouldEqual Decoder.Action.Push(bytes)
  }

  it should "pass part of bytes and take back rest of bytes if content length reached" in {
    val bytes = Bytes(1, 2, 3, 4, 5)
    val contentLength = 10L
    val (_, frame) = http11.decodeLimitedBody(7, bytes, contentLength)
    frame shouldEqual Decoder.Action.ForkFinish(
      bytes.slice(0, 3),
      bytes.slice(3, bytes.length)
    )
  }

  it should "finish the stream if count of bytes in chunk is equals to content length" in {
    val bytes = Bytes(1, 2, 3)
    val contentLength = 10L
    val (_, frame) = http11.decodeLimitedBody(7, bytes, contentLength)
    frame shouldEqual Decoder.Action.PushFinish(bytes)
  }

  // Request/Response

  private val genCookieOrParam =
    for {
      k <- Gen.asciiPrintableStr.filter(_.nonEmpty)
      v <- Gen.asciiPrintableStr
    } yield (k, v)

  private val genHeader =
    for {
      k <- Gen.alphaNumStr.filter(_.nonEmpty)
      v <- Gen.asciiPrintableStr
    } yield (k, v)

  private val genStatus =
    for {
      k <- Gen.choose(0, 1000)
      v <- Gen.alphaNumStr.filter(_.nonEmpty)
    } yield Response.Status(k, v.toUpperCase)

  private val genResponse =
    for {
      status <- genStatus
      //pathStrings <- Gen.listOf(Gen.asciiPrintableStr)
      //path = Path.fromString(pathStrings.mkString("/"))
      headers <- Gen.listOf(genHeader)
      bytes <- Gen.listOf(Gen.choose(Byte.MinValue, Byte.MaxValue))
      bytesVector = Bytes.wrap(bytes.toArray)
    } yield Stream(bytesVector)
      .mat()
      .map { bodyStream =>
        bytesVector -> Response(
          status = status,
          headers = headers,
          body = bodyStream,
          contentLength = Some(bytes.length.toLong)
        )
      }

  private val genRequest =
    for {
      method <- Gen.oneOf(Request.Method.All)
      pathStrings <- Gen.listOf(Gen.alphaNumStr)
      pq = PathAndQuery.fromString(pathStrings.mkString("/"))
      headers <- Gen.listOf(genHeader)
      cookies <- Gen.listOf(genCookieOrParam)
      params <- Gen.listOf(genCookieOrParam)
      bytes <- Gen.listOf(Gen.choose(Byte.MinValue, Byte.MaxValue))
      bytesVector = Bytes.wrap(bytes.toArray)
    } yield Stream(bytesVector)
      .mat()
      .map { bodyStream =>
        val originalRequest = Request(
          method = method,
          pq = pq,
          headers = headers,
          contentLength = Some(bytes.length.toLong),
          body = bodyStream
        )
        val withCookies = cookies.foldLeft(originalRequest) {
          case (r, (k, v)) =>
            r.withCookie(k, v)
        }
        val withCookiesAndParams = params.foldLeft(withCookies) {
          case (r, (k, v)) =>
            r.withParam(k, v)
        }
        bytesVector -> withCookiesAndParams
      }

  private def sliceToChunks(bytes: Bytes, n: Int) = {
    val chunk = bytes.length / n
    (0 until n) map { i =>
      val pos = chunk * i
      if (i < n - 1) bytes.slice(pos, pos + chunk)
      else bytes.slice(pos, bytes.length)
    }
  }

  "renderResponse/parseResponse" should "comply with the law `parse(render(response)) == response`" in {
    forAll (genResponse) { generated =>
      val (responseNoBody, bodyBytes, (parsedBodyBytes, parsedResponse)) = await {
        for {
          (bodyBytes, response) <- generated
          bytesStream <- http11.renderResponse(response)
          bytes <- bytesStream.fold(Bytes.empty)(_ ++ _)
          //_ = println(bytes.asciiString.replaceAll("\r", "\\\\r"))
          lhe = http11.findLastHeaderEnd(bytes)
        } yield {
          val responseNoBody = response.copy(body = ())
          (responseNoBody, bodyBytes, http11.parseResponse(bytes, lhe))
        }
      }
      assert(
        (parsedBodyBytes == bodyBytes) &&
        parsedResponse == responseNoBody
      )
    }
  }

  "renderResponse/decodeResponse" should "comply with rule `decode(render(response)) == response`" in {
    // FIXME fomkin: I do not understand why Gen.chooseNum gives values < 1
    forAll (genResponse, Gen.chooseNum(2, 20).filter(i => i > 1)) { (generated, numChunks) =>
      val (sampleResponse, decodedResponse, sampleBody, decodedBody) = await {
        for {
          (sampleBody, sampleResponse) <- generated
          renderedResponseAsStream <- http11.renderResponse(sampleResponse)
          renderedResponseAsBytes <- renderedResponseAsStream.fold(Bytes.empty)(_ ++ _)
          renderedResponseAsChunks <- Stream(sliceToChunks(renderedResponseAsBytes, numChunks):_*).mat()
          maybeDecodedResponse <- http11.decodeResponse(Decoder(renderedResponseAsChunks)).pull()
          decodedResponse = maybeDecodedResponse.getOrElse(throw new Exception("No response found in rendered stream"))
          decodedBody <- decodedResponse.body.fold(Bytes.empty)(_ ++ _)
        } yield (
          sampleResponse.copy(body = ()),
          decodedResponse.copy(body = ()),
          sampleBody,
          decodedBody
        )
      }
      assert(
        sampleResponse == decodedResponse &&
          (sampleBody == decodedBody)
      )
    }
  }

  "renderRequest/parseRequest" should "comply with the law `parse(render(request)) == request`" in {
    forAll (genRequest) { generated =>
      val (requestNoBody, bodyBytes, (parsedBodyBytes, parsedRequest)) = await {
        for {
          (bodyBytes, request) <- generated
          bytesStream <- http11.renderRequest(request)
          bytes <- bytesStream.fold(Bytes.empty)(_ ++ _)
          lhe = http11.findLastHeaderEnd(bytes)
        } yield {
          val responseNoBody = request.copy(body = ())
          (responseNoBody, bodyBytes, http11.parseRequest(bytes, lhe))
        }
      }
      assert(
        (parsedBodyBytes == bodyBytes) &&
          parsedRequest == requestNoBody
      )
    }
  }

  "renderRequest/decodeRequest" should "comply with rule `decode(render(request)) == request`" in {
    // FIXME fomkin: I do not understand why Gen.chooseNum gives values < 1
    forAll (genRequest, Gen.chooseNum(2, 20).filter(i => i > 1)) { (generated, numChunks) =>
      val (sampleRequest, decodedRequest, sampleBody, decodedBody) = await {
        for {
          (sampleBody, sampleRequest) <- generated
          renderedRequestAsStream <- http11.renderRequest(sampleRequest)
          renderedRequestAsBytes <- renderedRequestAsStream.fold(Bytes.empty)(_ ++ _)
          renderedRequestAsChunks <- Stream(sliceToChunks(renderedRequestAsBytes, numChunks):_*).mat()
          maybeDecodedRequest <- http11.decodeRequest(Decoder(renderedRequestAsChunks)).pull()
          decodedRequest = maybeDecodedRequest.getOrElse(throw new Exception("No request found in rendered stream"))
          decodedBody <- decodedRequest.body.fold(Bytes.empty)(_ ++ _)
        } yield (
          sampleRequest.copy(body = ()),
          decodedRequest.copy(body = ()),
          sampleBody,
          decodedBody
        )
      }
      assert(
        sampleRequest == decodedRequest &&
          (sampleBody == decodedBody)
      )
    }
  }

  // FIXME https://github.com/scalatest/scalatest/issues/1320
  private def await[T](process: Future[T]): T =
    process
      .ready(Duration.Inf)(null)
      .value
      .get
      .get
}
