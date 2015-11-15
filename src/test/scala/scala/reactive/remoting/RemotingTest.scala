package scala.reactive
package remoting



import java.io._
import java.net._
import java.nio._
import org.scalatest.FunSuite
import org.scalatest.Matchers



class RemotingTest extends FunSuite with Matchers {

  test("UDP transport should send events correctly") {
    // start server
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
        val socket = new DatagramSocket(21357)
        val packet = new DatagramPacket(new Array[Byte](1024), 1024)
        socket.receive(packet)
        val buffer = ByteBuffer.wrap(packet.getData, packet.getOffset, packet.getLength)
        
        {
          val inputStream = new ByteBufferInputStream(buffer)
          val objectInputStream = new ObjectInputStream(inputStream)
          assert(objectInputStream.readObject() == "test-iso")
        }
        {
          val inputStream = new ByteBufferInputStream(buffer)
          val objectInputStream = new ObjectInputStream(inputStream)
          assert(objectInputStream.readObject() == "test-anchor")
        }
        {
          val inputStream = new ByteBufferInputStream(buffer)
          val objectInputStream = new ObjectInputStream(inputStream)
          assert(objectInputStream.readObject() == "test-event")
        }

        success = true
      }
    }
    server.start()

    // start iso system
    val system = IsoSystem.default("test-system")
    try {
      val sysUrl = SystemUrl("iso.udp", "localhost", 21357)
      val channelUrl = ChannelUrl(IsoUrl(sysUrl, "test-iso"), "test-anchor")
      val channel = system.remoting.resolve[String](channelUrl)

      // send message
      channel ! "test-event"

      // wait for server shutdown
      server.join(2000)

      // check that server completed normally
      assert(server.success)
    } finally system.shutdown()
  }

}
