package scala.reactive
package remoting



import java.io.OutputStream
import java.io.ObjectOutputStream
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
      private val refSenderInstance = {
        val t = new Udp.Sender[AnyRef](
          this,
          new UnrolledRing[ChannelUrl],
          new UnrolledRing[AnyRef],
          ByteBuffer.allocateDirect(65535))
        t.start()
        t
      }
      implicit def refSender[T] = refSenderInstance.asInstanceOf[Udp.Sender[T]]
      implicit val intSender = {
        val t = new Udp.Sender[Int](
          this,
          new UnrolledRing[ChannelUrl],
          new UnrolledRing[Int],
          ByteBuffer.allocateDirect(65535))
        t.start()
        t
      }
      implicit val longSender = {
        val t = new Udp.Sender[Long](
          this,
          new UnrolledRing[ChannelUrl],
          new UnrolledRing[Long],
          ByteBuffer.allocateDirect(65535))
        t.start()
        t
      }
      implicit val doubleSender = {
        val t = new Udp.Sender[Double](
          this,
          new UnrolledRing[ChannelUrl],
          new UnrolledRing[Double],
          ByteBuffer.allocateDirect(65535))
        t.start()
        t
      }

      def newChannel[@spec(Int, Long, Double) T: Arrayable]
        (url: ChannelUrl): Channel[T] = {
        new UdpChannel[T](implicitly[Udp.Sender[T]], url)
      }
    }

    object Udp {
      class Sender[@spec(Int, Long, Double) T: Arrayable](
        val udpTransport: Udp,
        val urls: UnrolledRing[ChannelUrl],
        val events: UnrolledRing[T],
        val buffer: ByteBuffer
      ) extends Thread {
        setDaemon(true)

        private[remoting] def pickle[@spec(Int, Long, Double) T]
          (isoName: String, anchor: String, x: T) {
          val pickler = udpTransport.system.bundle.pickler
          buffer.clear()
          pickler.pickle(isoName, buffer)
          pickler.pickle(anchor, buffer)
          pickler.pickle(x, buffer)
          buffer.limit(buffer.position())
          buffer.position(0)
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

  /** Pickles an object into a byte buffer, so that it can be sent over the wire.
   */
  trait Pickler {
    def pickle[@spec(Int, Long, Double) T](x: T, buffer: ByteBuffer): Unit
  }

  object Pickler {
    /** Pickler implementation based on Java serialization.
     */
    class JavaSerialization extends Pickler {
      def pickle[@spec(Int, Long, Double) T](x: T, buffer: ByteBuffer) = {
        val outputStream = new ByteBufferOutputStream(buffer)
        val objectOutputStream = new ObjectOutputStream(outputStream)
        objectOutputStream.writeObject(x)
      }
    }
  }

  private class ByteBufferOutputStream(val buf: ByteBuffer) extends OutputStream {
    def write(b: Int): Unit = buf.put(b.toByte)
    override def write(bytes: Array[Byte], off: Int, len: Int): Unit = {
      buf.put(bytes, off, len)
    }
  }

}
