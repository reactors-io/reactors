package io.reactors






abstract class DataBuffer {
  def clear(): Unit

  def output: Data

  def input: Data

  def hasMore: Boolean
}


object DataBuffer {
  def streaming(batchSize: Int): DataBuffer = new Linked(batchSize)

  private[reactors] class Linked(val initialBatchSize: Int) extends DataBuffer {
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

    def hasMore: Boolean = {
      rawInput == rawOutput && rawInput.remainingReadSize == 0
    }
  }

  private[reactors] class LinkedData(
    private var rawBuffer: Linked,
    rawArray: Array[Byte]
  ) extends Data(rawArray, 0, 0) {
    private[reactors] var next: LinkedData = null

    def this(buffer: Linked, requestedBatchSize: Int) =
      this(buffer, new Array[Byte](requestedBatchSize))

    def buffer: Linked = rawBuffer

    private[reactors] def buffer_=(sb: Linked) = rawBuffer = sb

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