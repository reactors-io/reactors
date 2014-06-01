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

  private def init(dummy: ReactUnrolledQueue[T]) {
    ring = new UnrolledRing[T]
    insertsEmitter = new Reactive.Emitter[T]
    removesEmitter = new Reactive.Emitter[T]
  }

  init(this)

  def inserts: Reactive[T] = insertsEmitter

  def removes: Reactive[T] = removesEmitter

  def +=(elem: T) = {
    enqueue(elem)
    true
  }

  def -=(elem: T) = {
    ring.remove(elem)
  }

  def container = this

  def size = ring.size

  def foreach(f: T => Unit) = ring.foreach(f)

  def react = ???

  def enqueue(elem: T) {
    ring.enqueue(elem)
    insertsEmitter += elem
  }

  def dequeue(): T = {
    val elem = ring.dequeue()
    removesEmitter += elem
    elem
  }

}


object ReactUnrolledQueue {

  def apply[@spec(Int, Long, Double) T: Arrayable]() = new ReactUnrolledQueue[T]

  class Lifted[@spec(Int, Long, Double) T](val container: ReactUnrolledQueue[T]) extends ReactContainer.Lifted[T]

  implicit def factory[@spec(Int, Long, Double) T: Arrayable] = new ReactBuilder.Factory[T, ReactUnrolledQueue[T]] {
    def apply() = ReactUnrolledQueue[T]
  }

}