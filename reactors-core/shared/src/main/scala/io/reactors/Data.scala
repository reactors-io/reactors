package io.reactors






abstract class Data(private val raw: Array[Byte], var startPos: Int, var endPos: Int) {
  /** Flushes the contents of this chunk of data, and gets another one
   *  whose size is at least `minNextSize`.
   */
  def flush(minNextSize: Int): Data

  /** Acquires the next chunk of data for reading.
   */
  def fetch(): Data

  /** Updates this chunk of data at the position `pos`.
   */
  def update(pos: Int, b: Byte): Unit = raw(pos) = b

  /** Reads the byte at the position `pos`.
   */
  def apply(pos: Int): Byte = raw(pos)

  /** Remaining size for writing in this data chunk.
   */
  final def remainingWriteSize: Int = raw.length - endPos

  /** Remaining size for reading in this data chunk.
   */
  final def remainingReadSize: Int = endPos - startPos

  def byteString = raw.map(b => s"$b(${b.toChar})").mkString(", ")

  def fullByteString: String = {
    var curr = this
    var s = ""
    while (curr != null) {
      s += curr.byteString + "\n -> "
      curr = curr match {
        case linked: Data.Linked => linked.next
        case _ => null
      }
    }
    s
  }
}


object Data {
  private[reactors] class Linked(val defaultBatchSize: Int, requestedBatchSize: Int)
  extends Data(new Array(math.max(requestedBatchSize, defaultBatchSize)), 0, 0) {
    private[reactors] var next: Linked = null

    def flush(minNextSize: Int): Data = {
      next = new Linked(defaultBatchSize, minNextSize)
      next
    }

    def fetch(): Data = {
      next
    }
  }
}
