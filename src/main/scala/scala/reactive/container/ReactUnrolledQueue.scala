package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound
import util.UnrolledRing



class ReactUnrolledQueue[@spec(Int, Long, Double) T](implicit val arrayable: Arrayable[T])
extends ReactQueue[T] with ReactBuilder[T, ReactUnrolledQueue[T]] {
  private var ring: UnrolledRing[T] = _
  private var insertsEmitter: Reactive.Emitter[T] = _
  private var removesEmitter: Reactive.Emitter[T] = _
  private var headEmitter: Reactive.Emitter[T] = _

  private def init(dummy: ReactUnrolledQueue[T]) {
    ring = new UnrolledRing[T]
    insertsEmitter = new Reactive.Emitter[T]
    removesEmitter = new Reactive.Emitter[T]
    headEmitter = new Reactive.Emitter[T]
  }

  init(this)

  def inserts: Reactive[T] = insertsEmitter

  def removes: Reactive[T] = removesEmitter

  def +=(elem: T) = {
    enqueue(elem)
    insertsEmitter += elem
    true
  }

  def -=(elem: T) = {
    val prevhead = ring.head
    val at = ring.remove(elem)
    if (at != -1) {
      removesEmitter += elem
      if (at == 0) headEmitter += prevhead
      true
    } else false
  }

  def container = this

  def size = ring.size

  def foreach(f: T => Unit) = ring.foreach(f)

  val react = new ReactUnrolledQueue.Lifted(this)

  def enqueue(elem: T) {
    ring.enqueue(elem)
    insertsEmitter += elem
  }

  def dequeue(): T = {
    val elem = ring.dequeue()
    removesEmitter += elem
    elem
  }

  def head: T = ring.head

}


object ReactUnrolledQueue {

  def apply[@spec(Int, Long, Double) T: Arrayable]() = new ReactUnrolledQueue[T]

  class Lifted[@spec(Int, Long, Double) T](val container: ReactUnrolledQueue[T]) extends ReactContainer.Lifted[T] {
    def head: Reactive[T] = container.headEmitter
  }

  implicit def factory[@spec(Int, Long, Double) T: Arrayable] = new ReactBuilder.Factory[T, ReactUnrolledQueue[T]] {
    def apply() = ReactUnrolledQueue[T]
  }

}