package io.reactors
package remote



import io.reactors.DataBuffer.LinkedData



class TcpTransport(val system: ReactorSystem) extends Remote.Transport {
  private val dataPool = new TcpTransport.VariableDataSizePool(
    system.config.int("transport.tcp.data-chunk-pool.parallelism")
  )

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
    private val padding0 = 0L
    private val padding1 = 0L
    private val padding2 = 0L
    private val padding3 = 0L
    private val padding4 = 0L
    private val padding5 = 0L
    def allocate(): LinkedData = this.synchronized {
      val h = head
      if (h == null) null
      else {
        head = h.next
        h
      }
    }
    def deallocate(data: LinkedData): Unit = this.synchronized {
      data.next = head
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
        val data = freeLists(slot).allocate()
        if (data != null) return data
      } while (slot != initial)
      new LinkedData(buffer, dataSize, dataSize)
    }

    def deallocate(data: LinkedData): Unit = {
      val slot = Thread.currentThread.getId.toInt % parallelism
      freeLists(slot).deallocate(data)
    }
  }

  private[reactors] class VariableDataSizePool(val parallelism: Int) {
    private[reactors] val fixedPools = Array.tabulate(MAX_GROUPS) {
      i => new FixedDataSizePool(4 << i, parallelism)
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


  private[reactors] class Connection(
    val uid: Long,
    val url: SystemUrl,
    val tcp: TcpTransport
  ) {
    def flush(data: LinkedData) = ???
  }


  private[reactors] class SendBuffer(
    val pool: VariableDataSizePool,
    val connectionUid: Long,
    val connection: Connection,
    batchSize: Int
  ) extends DataBuffer.Streaming(batchSize) {
    protected[reactors] override def allocateData(minNextSize: Int): LinkedData = {
      pool.allocate(this, minNextSize)
    }

    protected[reactors] override def deallocateData(old: LinkedData): Unit = {
      // The data chunk is dispatched to the connection pool,
      // and must be deallocated by the connection pool.
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
    @transient var tcp: TcpTransport
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