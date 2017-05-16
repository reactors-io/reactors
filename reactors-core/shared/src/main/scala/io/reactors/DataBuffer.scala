package io.reactors






abstract class DataBuffer {
  def output: Data

  def input: Data
}


object DataBuffer {
  def streaming(batchSize: Int): DataBuffer = new Streaming(batchSize)

  private[reactors] class Streaming(val batchSize: Int) extends DataBuffer {
    private[remote] var rawOutput = new LinkedData(this, batchSize, batchSize)
    private[remote] var rawInput = new LinkedData(this, batchSize, batchSize)

    protected[reactors] def allocateData(minNextSize: Int): LinkedData = {
      new LinkedData(this, batchSize, minNextSize)
    }

    protected[reactors] def deallocateData(old: LinkedData) = {
    }

    protected[reactors] def onFlush(old: LinkedData): Unit = {
      rawOutput = old.next
    }

    protected[reactors] def onFetch(old: LinkedData): Unit = {
      if (old.next != null) {
        rawInput = old.next
        deallocateData(old)
      }
    }

    def output: Data = rawOutput

    def input: Data = rawInput
  }

  private[reactors] class LinkedData(
    val buffer: Streaming, val defaultBatchSize: Int, requestedBatchSize: Int
  ) extends Data(new Array(math.max(requestedBatchSize, defaultBatchSize)), 0, 0) {
    private[reactors] var next: LinkedData = null

    def flush(minNextSize: Int): Data = {
      next = buffer.allocateData(minNextSize)
      val result = next
      buffer.onFlush(this)
      result
    }

    def fetch(): Data = {
      val result = next
      buffer.onFetch(this)
      result
    }

    def fullByteString: String = {
      var curr = buffer.rawInput
      var s = ""
      while (curr != null) {
        s += curr.byteString + "\n -> "
        curr = curr match {
          case linked: DataBuffer.Streaming => linked.next
          case _ => null
        }
      }
      s
    }
  }
}
