package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound
import core.UnrolledRing



class RUnrolledQueue[@spec(Int, Long, Double) T](implicit val arrayable: Arrayable[T])
extends RQueue[T] {
  private var ring: UnrolledRing[T] = _
  private var insertsEmitter: Reactive.Emitter[T] = _
  private var removesEmitter: Reactive.Emitter[T] = _
  private var headEmitter: Reactive.Emitter[T] = _

  private def init(dummy: RUnrolledQueue[T]) {
    ring = new UnrolledRing[T]
    insertsEmitter = new Reactive.Emitter[T]
    removesEmitter = new Reactive.Emitter[T]
    headEmitter = new Reactive.Emitter[T]
  }

  init(this)

  def inserts: Reactive[T] = insertsEmitter

  def removes: Reactive[T] = removesEmitter

  def container = this

  def size = ring.size

  def foreach(f: T => Unit) = ring.foreach(f)

  val react = new RUnrolledQueue.Lifted(this)

  def enqueue(elem: T) {
    ring.enqueue(elem)
    insertsEmitter += elem
    if (size == 1) headEmitter += ring.head
  }

  def dequeue(): T = {
    val elem = ring.dequeue()
    removesEmitter += elem
    if (size > 0) headEmitter += ring.head
    elem
  }

  def head: T = ring.head

}


object RUnrolledQueue {

  def apply[@spec(Int, Long, Double) T: Arrayable]() = new RUnrolledQueue[T]

  class Lifted[@spec(Int, Long, Double) T](val container: RUnrolledQueue[T]) extends RContainer.Lifted[T] {
    def head: Reactive[T] = container.headEmitter
  }

}