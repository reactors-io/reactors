package org.reactress
package util






class UnrolledBuffer[@spec(Int, Long, Double) T](implicit val arrayable: Arrayable[T]) {
  import UnrolledBuffer._

  private[reactress] var head = new Node[T](this, arrayable.newNonNilArray(INITIAL_LENGTH))
  private[reactress] var last = head

  def +=(elem: T): this.type = {
    head += elem
    this
  }

  def foreach(f: T => Unit) {
    var node = head
    while (node != null) {
      var i = 0
      val array = node.array
      val until = node.size
      while (i < until) {
        f(array(i))
        i += 1
      }
      node = node.next
    }
  }

  def clear() {
    head = new Node[T](this, arrayable.newNonNilArray(INITIAL_LENGTH))
    last = head
  }
}


object UnrolledBuffer {

  def INITIAL_LENGTH = 8
  def MAX_LENGTH = 64

  class Node[@spec(Int, Long, Double) T](val outer: UnrolledBuffer[T], val array: Array[T]) {
    private[reactress] var size = 0
    private[reactress] var next: Node[T] = null

    def +=(elem: T) {
      if (size < array.length) {
        array(size) = elem
        size += 1
      } else {
        val nlen = math.min(MAX_LENGTH, array.length * 2)
        next = new Node(outer, outer.arrayable.newNonNilArray(nlen))
        outer.last = next
        next += elem
      }
    }
  }

}