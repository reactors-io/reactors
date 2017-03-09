package io.reactors






abstract class Data(val raw: Array[Byte], var pos: Int) {
  def flush(minNextSize: Int): Data

  final def spaceLeft: Int = raw.length - pos
}


object Data {
  class Linked(val defaultBatchSize: Int, requestedBatchSize: Int)
  extends Data(new Array(math.max(requestedBatchSize, defaultBatchSize)), 0) {
    private var rawNext: Linked = null
    def flush(minNextSize: Int): Data = {
      rawNext = new Linked(defaultBatchSize, requestedBatchSize)
      rawNext
    }
  }
}
