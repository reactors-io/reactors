package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound
import scala.reactive.core.BinaryHeap



class RBinaryHeap[@spec(Int, Long, Double) T](val initialSize: Int = 16)(implicit val arrayable: Arrayable[T], val order: Order[T])
extends RPriorityQueue[T] {
  private var heap: BinaryHeap[T] = _
  private var insertsEmitter: Reactive.Emitter[T] = _
  private var removesEmitter: Reactive.Emitter[T] = _
  private var headEmitter: Reactive.Emitter[T] = _

  def init(dummy: RBinaryHeap[T]) {
    heap = new BinaryHeap(initialSize)
    insertsEmitter = new Reactive.Emitter[T]
    removesEmitter = new Reactive.Emitter[T]
    headEmitter = new Reactive.Emitter[T]
  }

  init(this)

  def foreach(f: T => Unit): Unit = heap.foreach(f)

  def size = heap.size

  def inserts = insertsEmitter

  def removes = removesEmitter

  val react = new RBinaryHeap.Lifted(this)

  def enqueue(elem: T) {
    val oldHead = if (heap.nonEmpty) heap.head else arrayable.nil
    heap.enqueue(elem)
    val newHead = heap.head
    insertsEmitter += elem
    if (newHead != oldHead) headEmitter += newHead
  }

  def dequeue(): T = {
    val elem = heap.dequeue()
    removesEmitter += elem
    if (size > 0) headEmitter += heap.head
    elem
  }

  def head: T = heap.head

}


object RBinaryHeap {

  def apply[@spec(Int, Long, Double) T: Arrayable: Order](initialSize: Int) = new RBinaryHeap[T](initialSize)

  class Lifted[@spec(Int, Long, Double) T](val container: RBinaryHeap[T]) extends RPriorityQueue.Lifted[T] {
    def head: Reactive[T] = container.headEmitter
  }

}