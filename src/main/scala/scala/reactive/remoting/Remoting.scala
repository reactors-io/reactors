package scala.reactive
package remoting



import java.net._
import scala.collection._
import scala.reactive.core.UnrolledRing



class Remoting(val system: IsoSystem) extends Protocol {
  object Udp extends Remoting.Transport {
    val socket = new DatagramSocket(system.bundle.udpUrl.port)

    def newChannel[@spec(Int, Long, Double) T](url: ChannelUrl): Channel[T] = {
      ???
    }
  }

  private class UdpChannel[@spec(Int, Long, Double) T](url: ChannelUrl)
  extends Channel[T] {
    def !(x: T): Unit = ???
  }

  def resolve[T](channelUrl: ChannelUrl): Channel[T] = {
    channelUrl.isoUrl.systemUrl.schema match {
      case "iso.udp" => new UdpChannel(channelUrl)
      case s => sys.error("Unknown channel schema: $s")
    }
  }
}


object Remoting {
  trait Transport {
    def newChannel[@spec(Int, Long, Double) T](url: ChannelUrl): Channel[T]
  }
}
