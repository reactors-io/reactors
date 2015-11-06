package scala.reactive
package remoting



import java.io.OutputStream
import java.net._
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import scala.annotation.tailrec
import scala.collection._
import scala.reactive.core.UnrolledRing



class Remoting(val system: IsoSystem) extends Protocol {
  val udpTransport = new Remoting.Transport.Udp(system)

  def resolve[@spec(Int, Long, Double) T: Arrayable]
    (channelUrl: ChannelUrl): Channel[T] = {
    channelUrl.isoUrl.systemUrl.schema match {
      case "iso.udp" => udpTransport.newChannel[T](channelUrl)
      case s => sys.error("Unknown channel schema: $s")
    }
  }
}


object Remoting {
  trait Transport {
    def newChannel[@spec(Int, Long, Double) T: Arrayable](url: ChannelUrl): Channel[T]
  }

  object Transport {
    class Udp(val system: IsoSystem) extends Transport {
      val datagramChannel = {
        val url = system.bundle.udpUrl
        val ch = DatagramChannel.open()
        ch.bind(url.inetSocketAddress)
        ch
      }
      val senderMap = Map[Arrayable[_], Udp.Sender[_]](
        implicitly[Arrayable[Int]] -> newSender[Int],
        implicitly[Arrayable[Long]] -> newSender[Long],
        implicitly[Arrayable[Double]] -> newSender[Double],
        implicitly[Arrayable[AnyRef]] -> newSender[AnyRef]
      )

      def sender[T](a: Arrayable[T]) = senderMap(a).asInstanceOf[Udp.Sender[T]]

      def newChannel[@spec(Int, Long, Double) T: Arrayable]
        (url: ChannelUrl): Channel[T] = {
        new UdpChannel[T](sender(implicitly[Arrayable[T]]), url)
      }

      def newSender[@spec(Int, Long, Double) T: Arrayable]: Udp.Sender[T] = {
        val t = new Udp.Sender[T](this)
        t.start()
        t
      }
    }

    object Udp {
      class Sender[@spec(Int, Long, Double) T: Arrayable](val udpTransport: Udp)
      extends Thread {
        val urls = new UnrolledRing[ChannelUrl]
        val events = new UnrolledRing[T]
        val buffer = ByteBuffer.allocateDirect(65535)

        setDaemon(true)

        private[remoting] def pickle[@spec(Int, Long, Double) T]
          (iso: String, ch: String, x: T) {
          val pickler = udpTransport.system.bundle.pickler
          buffer.clear()
          pickler.pickle(iso, buffer)
          pickler.pickle(ch, buffer)
          pickler.pickle(x, buffer)
        }

        private[remoting] def send[@spec(Int, Long, Double) T](x: T, url: ChannelUrl) {
          pickle(url.isoUrl.name, url.anchor, x)
          val sysUrl = url.isoUrl.systemUrl
          udpTransport.datagramChannel.send(buffer, sysUrl.inetSocketAddress)
        }

        def enqueue(x: T, url: ChannelUrl) {
          this.synchronized {
            urls.enqueue(url)
            events.enqueue(x)
            this.notify()
          }
        }

        @tailrec
        final override def run() {
          var url: ChannelUrl = null
          var x: T = null.asInstanceOf[T]
          this.synchronized {
            while (urls.isEmpty) this.wait()
            url = urls.dequeue()
            x = events.dequeue()
          }
          send(x, url)
          run()
        }
      }
    }

    private class UdpChannel[@spec(Int, Long, Double) T](
      sender: Udp.Sender[T], url: ChannelUrl
    ) extends Channel[T] {
      def !(x: T): Unit = sender.enqueue(x, url)
    }
  }

  trait Pickler {
    def pickle[@spec T](x: T, buffer: ByteBuffer): Unit
  }

  class JavaSerializationPickler extends Pickler {
    def pickle[@spec T](x: T, buffer: ByteBuffer) = ???
  }

  private class ByteBufferOutputStream(val buf: ByteBuffer) extends OutputStream {
    def write(b: Int): Unit = buf.put(b.toByte)
    override def write(bytes: Array[Byte], off: Int, len: Int): Unit = {
      buf.put(bytes, off, len)
    }
  }

}
