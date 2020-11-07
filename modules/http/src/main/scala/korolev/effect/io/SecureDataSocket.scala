//package korolev.effect.io
//
//import java.nio.ByteBuffer
//
//import javax.net.ssl.{SSLContext, SSLEngine}
//import korolev.effect.Effect
//
//class SecureDataSocket[F: Effect](sslEngine: SSLEngine, socket: RawDataSocket[F]) {
//
//  val b1 = ByteBuffer.allocate(10)
//  val b2 = ByteBuffer.allocate(10)
//
//  val result = sslEngine.unwrap(b1, b2)
//
//}
