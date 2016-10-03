package io.reactors
package remote



import io.reactors.common.UnrolledRing
import java.io._
import java.net._
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import scala.annotation.tailrec
import scala.collection._



class UdpTransport(val system: ReactorSystem) extends Remote.Transport {
  private[remote] val datagramChannel = {
    val url = system.bundle.urlMap("reactor.udp").url
    val ch = DatagramChannel.open()
    ch.bind(url.inetSocketAddress)
    ch
  }

  def port: Int = datagramChannel.socket.getLocalPort

  private val refSenderInstance = {
    val t = new UdpTransport.Sender[AnyRef](
      this,
      new UnrolledRing[ChannelUrl],
      new UnrolledRing[AnyRef],
      ByteBuffer.allocateDirect(65535))
    t.start()
    t
  }

  private implicit def refSender[T] =
    refSenderInstance.asInstanceOf[UdpTransport.Sender[T]]

  private implicit val intSender = {
    val t = new UdpTransport.Sender[Int](
      this,
      new UnrolledRing[ChannelUrl],
      new UnrolledRing[Int],
      ByteBuffer.allocateDirect(65535))
    t.start()
    t
  }

  private implicit val longSender = {
    val t = new UdpTransport.Sender[Long](
      this,
      new UnrolledRing[ChannelUrl],
      new UnrolledRing[Long],
      ByteBuffer.allocateDirect(65535))
    t.start()
    t
  }

  private implicit val doubleSender = {
    val t = new UdpTransport.Sender[Double](
      this,
      new UnrolledRing[ChannelUrl],
      new UnrolledRing[Double],
      ByteBuffer.allocateDirect(65535))
    t.start()
    t
  }

  private val receiver = {
    val t = new UdpTransport.Receiver(this, ByteBuffer.allocateDirect(65535))
    t.start()
    t
  }

  def newChannel[@spec(Int, Long, Double) T: Arrayable]
    (url: ChannelUrl): Channel[T] = {
    new UdpTransport.UdpChannel[T](implicitly[UdpTransport.Sender[T]], url)
  }

  def shutdown() {
    datagramChannel.socket.close()
    refSender.notifyEnd()
    intSender.notifyEnd()
    longSender.notifyEnd()
    doubleSender.notifyEnd()
    receiver.notifyEnd()
  }
}


object UdpTransport {
  private[remote] class Sender[@spec(Int, Long, Double) T: Arrayable](
    val udpTransport: UdpTransport,
    val urls: UnrolledRing[ChannelUrl],
    val events: UnrolledRing[T],
    val buffer: ByteBuffer
  ) extends Thread {
    setDaemon(true)

    private[remote] def pickle[@spec(Int, Long, Double) T]
      (isoName: String, anchor: String, x: T) {
      val pickler = udpTransport.system.bundle.pickler
      buffer.clear()
      pickler.pickle(isoName, buffer)
      pickler.pickle(anchor, buffer)
      pickler.pickle(x, buffer)
      buffer.limit(buffer.position())
      buffer.position(0)
    }

    private[remote] def send[@spec(Int, Long, Double) T](x: T, url: ChannelUrl) {
      pickle(url.reactorUrl.name, url.anchor, x)
      val sysUrl = url.reactorUrl.systemUrl
      udpTransport.datagramChannel.send(buffer, sysUrl.inetSocketAddress)
    }

    def enqueue(x: T, url: ChannelUrl) {
      this.synchronized {
        urls.enqueue(url)
        events.enqueue(x)
        this.notify()
      }
    }

    def notifyEnd() {
      this.synchronized {
        this.notify()
      }
    }

    @tailrec
    final override def run() {
      var url: ChannelUrl = null
      var x: T = null.asInstanceOf[T]
      def mustEnd = udpTransport.datagramChannel.socket.isClosed
      this.synchronized {
        while (urls.isEmpty && !mustEnd) this.wait()
        if (urls.nonEmpty) {
          url = urls.dequeue()
          x = events.dequeue()
        }
      }
      if (url != null) send(x, url)
      if (!mustEnd) run()
    }
  }

  private[remote] class Receiver(
    val udpTransport: UdpTransport,
    val buffer: ByteBuffer
  ) extends Thread {
    def notifyEnd() {
      // no op
    }

    def receive() {
      val socketAddress = udpTransport.datagramChannel.receive(buffer)
      buffer.flip()
      val pickler = udpTransport.system.bundle.pickler
      val isoName = pickler.depickle[String](buffer)
      val channelName = pickler.depickle[String](buffer)
      val event = pickler.depickle[AnyRef](buffer)
      udpTransport.system.channels.get[AnyRef](isoName, channelName) match {
        case Some(ch) => ch ! event
        case None => // drop event -- no such channel here
      }
    }

    @tailrec
    override final def run() {
      var success = false
      try {
        buffer.clear()
        receive()
        success = true
      } catch {
        case e: Exception => // not ok
      }
      if (success) run()
    }
  }

  private class UdpChannel[@spec(Int, Long, Double) T](
    sender: UdpTransport.Sender[T], url: ChannelUrl
  ) extends Channel[T] {
    def !(x: T): Unit = sender.enqueue(x, url)
  }
}
