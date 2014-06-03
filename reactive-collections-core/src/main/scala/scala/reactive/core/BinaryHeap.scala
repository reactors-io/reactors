package scala.reactive
package core





class BinaryHeap[@specialized(Int, Long, Double) T](val initialSize: Int = 16)(implicit val arrayable: Arrayable[T]) {
  private var array: Array[T] = arrayable.newArray(initialSize)
  private var sz = 0

  def enqueue(elem: T): Unit = ???

  def dequeue(): Unit = ???

  def head: T = ???

  def size: Int = sz

  def isEmpty = sz == 0

  def nonEmpty = !isEmpty

  def foreach(f: T => Unit): Unit = ???

  def clear() {
    array = arrayable.newArray(initialSize)
    sz = 0
  }

}

