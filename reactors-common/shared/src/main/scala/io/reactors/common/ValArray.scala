package io.reactors.common






class ValArray[@specialized(Byte, Char, Int, Long, Float, Double) T](
  private var raw: Array[T]
) {
  def apply(i: Int): T = raw(i)

  def length: Int = raw.length
}
