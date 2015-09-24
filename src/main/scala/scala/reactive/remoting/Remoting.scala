package scala.reactive
package remoting



import io.netty.bootstrap._
import io.netty.buffer._
import scala.collection._
import scala.reactive.core.UnrolledRing



class Remoting(val system: IsoSystem) extends Protocol {
  def resolve[T](channelUrl: ChannelUrl): Channel[T] = {
    ???
  }
}
