package scala.reactive
package remoting



import io.netty.bootstrap._
import io.netty.buffer._
import scala.collection._



class Remoting(val system: IsoSystem) extends Protocol {
  private val connections = mutable.Map[SystemUrl, AnyRef]()

  def resolve[T](channelUrl: ChannelUrl): Channel[T] = ???
}
