package scala.reactive
package remoting



import java.io._
import java.net._
import java.nio._
import org.scalatest.FunSuite
import org.scalatest.Matchers
import scala.concurrent._
import scala.concurrent.duration._



class RemotingTest extends FunSuite with Matchers {

  test("UDP transport should send events correctly") {
    // start server
    val socket = new DatagramSocket
    val server = new Thread {
      var success = false

      class ByteBufferInputStream(val buffer: ByteBuffer) extends InputStream {
        def read() = buffer.get()
        override def read(dst: Array[Byte], offset: Int, length: Int) = {
          val count = math.min(buffer.remaining, length)
          if (count == 0) -1
          else {
            buffer.get(dst, offset, length)
            count
          }
        }
      }

      override def run() {
        val packet = new DatagramPacket(new Array[Byte](1024), 1024)
        socket.receive(packet)
        val buffer = ByteBuffer.wrap(packet.getData, packet.getOffset, packet.getLength)
        
        def read(): Any = {
          val inputStream = new ByteBufferInputStream(buffer)
          val objectInputStream = new ObjectInputStream(inputStream)
          objectInputStream.readObject()
        }

        assert(read() == "test-iso")
        assert(read() == "test-anchor")
        assert(read() == "test-event")

        success = true
      }
    }
    server.start()

    // start iso system
    val system = IsoSystem.default("test-system")
    try {
      val port = socket.getLocalPort
      val sysUrl = SystemUrl("iso.udp", "localhost", port)
      val channelUrl = ChannelUrl(IsoUrl(sysUrl, "test-iso"), "test-anchor")
      val channel = system.remoting.resolve[String](channelUrl)

      // send message
      channel ! "test-event"

      // wait for server shutdown
      server.join(9000)

      // check that server completed normally
      assert(server.success)
    } finally system.shutdown()
  }

  test("UDP transport should send and receive events correctly") {
    // start two iso systems
    val sendSys = IsoSystem.default(
      "test-send-sys",
      new IsoSystem.Bundle(Scheduler.default, "remoting.udp.port = 0"))
    val recvSys = IsoSystem.default(
      "test-recv-sys",
      new IsoSystem.Bundle(Scheduler.default, "remoting.udp.port = 0"))
    try {
      // prepare channel
      val sysUrl =
        SystemUrl("iso.udp", "localhost", recvSys.remoting.udpTransport.port)
      val channelUrl =
        ChannelUrl(IsoUrl(sysUrl, "test-iso"), "test-anchor")
      val ch = sendSys.remoting.resolve[String](channelUrl)

      // start receiving isolate
      val started = Promise[Boolean]()
      val received = Promise[Boolean]()
      val receiverProto =
        Proto[RemotingTest.UdpReceiver](started, received)
          .withName("test-iso").withChannelName("test-anchor")
      recvSys.isolate(receiverProto)
      assert(Await.result(started.future, 10.seconds))
      
      // send event and wait
      ch ! "test-event"
      assert(Await.result(received.future, 10.seconds))
    } finally {
      sendSys.shutdown()
      recvSys.shutdown()
    }
  }

}


object RemotingTest {
  class UdpReceiver(val started: Promise[Boolean], val received: Promise[Boolean])
  extends Iso[String] {
    import implicits.canLeak
    sysEvents onMatch {
      case IsoStarted => started.success(true)
    }
    main.events onMatch {
      case "test-event" => received.success(true)
    }
  }
}
