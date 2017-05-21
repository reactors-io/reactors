package io.reactors
package remote



import io.reactors.DataBuffer.LinkedData
import scala.collection.concurrent.TrieMap



class TcpTransport(val system: ReactorSystem) extends Remote.Transport {
  private[reactors] val dataPool = new TcpTransport.VariableDataSizePool(
    system.config.int("transport.tcp.data-chunk-pool.parallelism")
  )
  private[reactors] val staging = new TcpTransport.Staging(this)

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
  private[reactors] val MAX_GROUPS = 8
  private[reactors] val MIN_SIZE = 8

  private[reactors] class FreeList {
    private var head: LinkedData = null
    // Aligning so that the free list is alone in the cache line.
    private val padding0 = 0L
    private val padding1 = 0L
    private val padding2 = 0L
    private val padding3 = 0L
    private val padding4 = 0L
    private val padding5 = 0L
    def allocate(buffer: SendBuffer): LinkedData = this.synchronized {
      val h = head
      if (h == null) null
      else {
        head = h.next
        h.buffer = buffer
        h
      }
    }
    def deallocate(data: LinkedData): Unit = this.synchronized {
      data.next = head
      data.buffer = null
      head = data
    }
  }

  private[reactors] class FixedDataSizePool(val dataSize: Int, val parallelism: Int) {
    private val freeLists = new Array[FreeList](parallelism)
    for (i <- 0 until parallelism) freeLists(i) = new FreeList

    def allocate(buffer: SendBuffer): LinkedData = {
      val initial = Thread.currentThread.getId.toInt % parallelism
      var slot = initial
      do {
        slot = (slot + 1) % parallelism
        val data = freeLists(slot).allocate(buffer)
        if (data != null) return data
      } while (slot != initial)
      new LinkedData(buffer, dataSize)
    }

    def deallocate(data: LinkedData): Unit = {
      val slot = Thread.currentThread.getId.toInt % parallelism
      freeLists(slot).deallocate(data)
    }
  }

  private[reactors] class VariableDataSizePool(val parallelism: Int) {
    private[reactors] val fixedPools = Array.tabulate(MAX_GROUPS) {
      i => new FixedDataSizePool(MIN_SIZE << i, parallelism)
    }

    private[reactors] def fixedPool(minNextSize: Int): FixedDataSizePool = {
      val highbit = Integer.highestOneBit(minNextSize)
      require((highbit << 1) != 0)
      val size = if (highbit == minNextSize) highbit else highbit << 1
      val group = math.max(MIN_SIZE, Integer.numberOfTrailingZeros(size)) - MIN_SIZE
      return fixedPools(group)
    }

    def allocate(buffer: SendBuffer, minNextSize: Int): LinkedData = {
      fixedPool(minNextSize).allocate(buffer)
    }

    def deallocate(old: LinkedData): Unit = {
      require(old.totalSize == Integer.highestOneBit(old.totalSize))
      fixedPool(old.totalSize).deallocate(old)
    }
  }

  private[reactors] class SendBuffer(
    val tcp: TcpTransport,
    var destination: Destination,
    var channel: TcpChannel[_]
  ) extends DataBuffer.Streaming(128) {
    def attachTo(newChannel: TcpChannel[_]) = {
      channel = newChannel
      destination = tcp.staging.resolve(channel.channelUrl.reactorUrl.systemUrl)
    }

    protected[reactors] override def allocateData(minNextSize: Int): LinkedData = {
      tcp.dataPool.allocate(this, minNextSize)
    }

    protected[reactors] override def deallocateData(old: LinkedData): Unit = {
      // The data chunk is dispatched to the connection pool,
      // and must be deallocated by the connection pool.
    }

    protected[reactors] override def onFlush(old: LinkedData): Unit = {
      super.onFlush(old)
      destination.flush(old)
    }

    protected[reactors] override def onFetch(old: LinkedData): Unit = {
      super.onFetch(old)
    }
  }

  private[reactors] class TcpChannel[@spec(Int, Long, Double) T](
    @transient var tcp: TcpTransport,
    val channelUrl: ChannelUrl
  ) extends Channel[T] {
    def send(x: T): Unit = {
      val reactorThread = Reactor.currentReactorThread
      // val context = reactorThread.marshalContext
      var dataBuffer = reactorThread.dataBuffer

      // Initialize data buffer if necessary.
      if (dataBuffer == null) {
        reactorThread.dataBuffer = new SendBuffer(tcp, null, null)
        dataBuffer = reactorThread.dataBuffer
      }

      // Check if the data buffer refers to this channel.
      val sendBuffer = dataBuffer.asInstanceOf[SendBuffer]
      if (sendBuffer.channel ne this) {
        sendBuffer.attachTo(this)
      }

      // Use the runtime marshaler to serialize the object.
      RuntimeMarshaler.marshal(channelUrl.reactorUrl.name, sendBuffer, false)
      RuntimeMarshaler.marshal(channelUrl.channelName, sendBuffer, false)
      RuntimeMarshaler.marshal(x, sendBuffer, true)
    }
  }

  private[reactors] class Staging(val tcp: TcpTransport) {
    private[reactors] val destinations = TrieMap[SystemUrl, Destination]()

    def resolve(systemUrl: SystemUrl): Destination = {
      val dst = destinations.lookup(systemUrl)
      if (dst != null) dst
      else {
        destinations.getOrElseUpdate(systemUrl, new Destination(systemUrl, tcp))
      }
    }
  }

  private[reactors] class Destination(
    val url: SystemUrl,
    val tcp: TcpTransport
  ) {
    def flush(data: LinkedData) = ???
  }

}
