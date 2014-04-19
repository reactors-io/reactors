package org.reactress
package util



import scala.annotation.tailrec



class UnrolledRing[@specialized(Int, Long, Double) T](implicit val arrayable: Arrayable[T]) {
  import UnrolledRing._

  private[reactress] var start: Node[T] = _
  private[reactress] var end: Node[T] = _

  private[reactress] def init(a: Arrayable[T]) {
    start = new Node(arrayable.newRawArray(INITIAL_NODE_LENGTH), 0, 0)
    start.next = start
    end = start
  }

  init(arrayable)

  private[reactress] final def advance() {
    if (start.isEmpty && start.next.nonEmpty && start != end) {
      start = start.next
    }
  }

  def nonEmpty: Boolean = {
    if (start.nonEmpty) true
    else {
      advance()
      start.nonEmpty
    }
  }

  def isEmpty: Boolean = !nonEmpty

  def head: T = {
    if (nonEmpty) start.head
    else throw new NoSuchElementException("empty")
  }

  def enqueue(elem: T) {
    end.enqueue(this, elem)
  }

  def dequeue(): T = {
    advance()
    start.dequeue(this)
  }

  def debugString = {
    var chain = ""
    var n = start
    do {
      var ptr = ""
      if (n == start) ptr += "$"
      if (n == end) ptr += "^"
      chain += s"$ptr[${n.start}, ${n.until}: ${n.array.mkString(", ")}] --> "
      n = n.next
    } while (n != start)
    s"UnrolledRing($chain)"
  }

}


object UnrolledRing {

  val INITIAL_NODE_LENGTH = 8
  val MAXIMUM_NODE_LENGTH = 128

  class Node[@specialized(Int, Long, Double) T](val array: Array[T], var start: Int, var until: Int) {
    var next: Node[T] = null

    final def isEmpty = start == until

    final def nonEmpty = !isEmpty

    def head = array(start)

    def reset() {
      start = 0
      until = 0
    }

    private def reserve(ring: UnrolledRing[T]) {
      val nextlen = math.min(MAXIMUM_NODE_LENGTH, array.length * 4)
      val fresh = new Node[T](ring.arrayable.newRawArray(nextlen), 0, 0)
      fresh.next = this.next
      this.next = fresh
      ring.end = fresh
    }

    def enqueue(ring: UnrolledRing[T], elem: T) {
      if (until < array.length) {
        array(until) = elem
        until += 1
        if (until == array.length) reserve(ring)
      } else if (this.isEmpty) {
        this.reset()
        this.enqueue(ring, elem)
      } else if (next.isEmpty) {
        next.reset()
        next.enqueue(ring, elem)
        ring.end = next
      } else {
        reserve(ring)
        this.next.enqueue(ring, elem)
      }
    }

    def dequeue(ring: UnrolledRing[T]): T = {
      if (isEmpty) throw new NoSuchElementException("empty")

      val elem = array(start)
      array(start) = null.asInstanceOf[T]
      start += 1
      elem
    }
  }

}
