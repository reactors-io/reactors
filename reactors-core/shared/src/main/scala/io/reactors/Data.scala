package io.reactors






abstract class Data(val raw: Array[Byte], var pos: Int) {
  def flush(): Data

  final def spaceLeft: Int = raw.length - pos
}
