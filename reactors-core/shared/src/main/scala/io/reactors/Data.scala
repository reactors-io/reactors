package io.reactors






abstract class Data(val chunk: Array[Byte], var pos: Int) {
  def flush(): Unit
}
