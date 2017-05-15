package io.reactors
package remote



import java.io._
import java.net._



class TcpTransport extends Remote.Transport {
  override def newChannel[@spec(Int, Long, Double) T: Arrayable](
    url: ChannelUrl
  ): Channel[T] = {
    ???
  }

  override def schema: String = "tcp"

  override def port: Int = ???

  override def shutdown(): Unit = {
  }
}


object TcpTransport {
  private[reactors] class TcpChannel[@spec(Int, Long, Double) T](
  ) extends Channel[T] {
    def send(x: T): Unit = {
      val reactorThread = Reactor.currentReactorThread
      val context = reactorThread.marshalContext
      val dataCache = reactorThread.dataCache
      
      // Obtain a data object from the pool if necessary.
      ???

      // Use the runtime marshaler to serialize the object.
    }
  }
}
