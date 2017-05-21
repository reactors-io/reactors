package io.reactors






abstract class DataBuffer {
  def clear(): Unit

  def output: Data

  def input: Data
}


object DataBuffer {
  def streaming(batchSize: Int): DataBuffer = new Streaming(batchSize)

  private[reactors] class Streaming(val initialBatchSize: Int) extends DataBuffer {
    private[reactors] var rawOutput = allocateData(initialBatchSize)
    private[reactors] var rawInput = rawOutput

    protected[reactors] def allocateData(minNextSize: Int): LinkedData = {
      new LinkedData(this, math.max(initialBatchSize, minNextSize))
    }

    protected[reactors] def deallocateData(old: LinkedData) = {
    }

    protected[reactors] def onFlush(old: LinkedData): Unit = {
      rawOutput = old.next
    }

    protected[reactors] def onFetch(old: LinkedData): Unit = {
      rawInput = old.next
    }

    def clear(): Unit = {
      rawOutput = allocateData(initialBatchSize)
      rawInput = rawOutput
    }

    def output: Data = rawOutput

    def input: Data = rawInput
  }

  private[reactors] class LinkedData(
    private var rawBuffer: Streaming,
    requestedBatchSize: Int
  ) extends Data(new Array(requestedBatchSize), 0, 0) {
    private[reactors] var next: LinkedData = null

    def buffer: Streaming = rawBuffer

    private[reactors] def buffer_=(sb: Streaming) = rawBuffer = sb

    def flush(minNextSize: Int): Data = {
      next = buffer.allocateData(minNextSize)
      val result = next
      buffer.onFlush(this)
      result
    }

    def fetch(): Data = {
      val result = next
      if (result != null) {
        buffer.onFetch(this)
        buffer.deallocateData(this)
      }
      // After this point, the `Data` object is potentially deallocated
      // and must not be used again.
      result
    }

    def fullByteString: String = {
      var curr = buffer.rawInput
      var s = ""
      while (curr != null) {
        s += curr.byteString + "\n -> "
        curr = curr match {
          case linked: DataBuffer.LinkedData => linked.next
          case _ => null
        }
      }
      s
    }
  }
}
