package scala.reactive
package remoting



import io.netty.bootstrap._
import io.netty.buffer._
import scala.collection._
import scala.reactive.core.UnrolledRing



class Remoting(val system: IsoSystem) extends Protocol {
  private class UdpChannel[T](url: ChannelUrl) extends Channel[T] {
    def !(x: T): Unit = ???

    def isSealed: Boolean = ???
  }

  def resolve[T](channelUrl: ChannelUrl): Channel[T] = {
    channelUrl.isoUrl.systemUrl.schema match {
      case "rc.udp" => new UdpChannel(url)
      case s => sys.error("Unknown channel schema: $s")
    }
  }
}
