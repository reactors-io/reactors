package io.reactors
package common






class HashMatrix[@specialized(Int, Long, Double) T](val initialSize: Int = 16)(
  implicit val arrayable: Arrayable[T]
) {

  def apply(x: Int, y: Int) = 0

  def update(x: Int, y: Int, v: T) = {}

}
