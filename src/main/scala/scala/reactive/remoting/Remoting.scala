package scala.reactive
package remoting



import java.io.OutputStream
import java.net._
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import scala.collection._
import scala.reactive.core.UnrolledRing



class Remoting(val system: IsoSystem) extends Protocol {
  object Udp extends Remoting.Transport {
    val datagramChannel = {
      val url = system.bundle.udpUrl
      val ch = DatagramChannel.open()
      ch.bind(url.inetSocketAddress)
      ch
    }

    def newChannel[@spec(Int, Long, Double) T](url: ChannelUrl): Channel[T] = {
      new UdpChannel[T](url)
    }

    private def serialize(name: String, anchor: String, x: Any): ByteBuffer = {
      ???
    }

    private class UdpChannel[@spec(Int, Long, Double) T](url: ChannelUrl)
    extends Channel[T] {
      def !(x: T): Unit = {
        val bytes = serialize(url.isoUrl.name, url.anchor, x)
        val sysUrl = url.isoUrl.systemUrl
        datagramChannel.send(bytes, sysUrl.inetSocketAddress)
      }
    }
  }

  def resolve[@spec(Int, Long, Double) T](channelUrl: ChannelUrl): Channel[T] = {
    channelUrl.isoUrl.systemUrl.schema match {
      case "iso.udp" => Udp.newChannel[T](channelUrl)
      case s => sys.error("Unknown channel schema: $s")
    }
  }
}


object Remoting {
  trait Transport {
    def newChannel[@spec(Int, Long, Double) T](url: ChannelUrl): Channel[T]
  }

  private class ByteBufferOutputStream(val buf: ByteBuffer) extends OutputStream {
    def write(b: Int): Unit = buf.put(b.toByte)
    override def write(bytes: Array[Byte], off: Int, len: Int): Unit = {
      buf.put(bytes, off, len)
    }
    
  }
}
