import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import korolev.blazeServer.{BlazeServerConfig, defaultExecutor}
import org.http4s.blaze.channel.nio2.NIO2SocketServerGroup
import org.http4s.blaze.channel.{BufferPipelineBuilder, ServerChannel}
import org.http4s.blaze.http.HttpServerStage
import org.http4s.blaze.pipeline.LeafBuilder
import org.scalatest.{BeforeAndAfterAll, FunSuite}

class MasterSuite extends FunSuite with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  var server = Option.empty[ServerChannel]

  override def beforeAll(): Unit = {
    val service = testApp.TestApp.service
    val config = BlazeServerConfig(
      port = 8000,
      doNotBlockCurrentThread = true
    )
    server = Some(korolev.blazeServer.runServer(service, config))
  }

  override def nestedSuites = Vector(
    new AddTodoSuite(Caps(Caps.Edge, "14.14393", "Windows 10"))//,
    //new AddTodoSuite(Caps(Caps.Chrome, "49.0", "Windows 10"))
  )

  override def afterAll(): Unit = {
    server.foreach(_.close())
  }
}
