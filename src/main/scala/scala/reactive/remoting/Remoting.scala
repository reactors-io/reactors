package scala.reactive
package remoting



import io.netty.bootstrap._



class Remoting(val system: IsoSystem) extends Protocol {
  def resolve[T](channelUrl: ChannelUrl): Channel[T] = ???
}
