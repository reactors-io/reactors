package io.reactors






abstract class Data(val raw: Array[Byte], var startPos: Int, var endPos: Int) {
  def flush(minNextSize: Int): Data

  def fetch(): Data

  def update(pos: Int, b: Byte): Unit = raw(pos) = b

  def apply(pos: Int): Byte = raw(pos)

  final def remainingWriteSize: Int = raw.length - endPos

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
