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
    val config = BlazeServerConfig(port = 8000)
    val f: BufferPipelineBuilder = _ => LeafBuilder(new HttpServerStage(1024*1024, 10*1024)(service))
    val group = AsynchronousChannelGroup.withThreadPool(defaultExecutor)
    val factory = NIO2SocketServerGroup(config.bufferSize, Some(group))

    server = Some(factory.
      bind(new InetSocketAddress(config.port), f).
      getOrElse(sys.error("Failed to bind server")))
  }

  override def nestedSuites = Vector(
    new AddTodoSuite
  )

  override def afterAll(): Unit = {
    server.foreach(_.close())
  }
}
