package io.reactors
package remote



import java.io._
import java.net._
import io.reactors.DataBuffer.LinkedData



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
  private[reactors] class BufferPool {
    def allocate(minNextSize: Int): LinkedData = ???

    def deallocate(old: LinkedData): Unit = ???
  }

  private[reactors] class Connection(val uid: Long, val url: SystemUrl) {
    def flush(data: LinkedData) = ???
  }

  private[reactors] class SendBuffer(
    val pool: BufferPool,
    val connectionUid: Long,
    val connection: Connection,
    batchSize: Int
  ) extends DataBuffer.Streaming(batchSize) {
    protected[reactors] override def allocateData(minNextSize: Int): LinkedData = {
      pool.allocate(minNextSize)
    }

    protected[reactors] override def deallocateData(old: LinkedData): Unit = {
      pool.deallocate(old)
    }

    protected[reactors] override def onFlush(old: LinkedData): Unit = {
      super.onFlush(old)
      connection.flush(old)
    }

    protected[reactors] override def onFetch(old: LinkedData): Unit = {
      super.onFetch(old)
    }
  }

  private[reactors] class TcpChannel[@spec(Int, Long, Double) T](
  ) extends Channel[T] {
    def send(x: T): Unit = {
      val reactorThread = Reactor.currentReactorThread
      val context = reactorThread.marshalContext
      val dataBuffer = reactorThread.dataBuffer

      // Obtain a data object from the pool if necessary.
      ???

      // Use the runtime marshaler to serialize the object.
    }
  }
}
