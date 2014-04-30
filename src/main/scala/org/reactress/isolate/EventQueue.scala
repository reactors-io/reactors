package org.reactress
package isolate






trait EventQueue[@spec(Int, Long, Double) T]
extends Reactive.Default[T] with Enqueuer[T] {
  def dequeue(): EventQueue[T]
  def isEmpty: Boolean
  def nonEmpty = !isEmpty
}


object EventQueue {

  class SyncedUnrolledRing[@spec(Int, Long, Double) T: Arrayable](val monitor: AnyRef)
  extends EventQueue[T] with Reactive.Default[T] {
    private[reactress] val ring = new util.UnrolledRing[T]
    def +=(elem: T) = monitor.synchronized {
      ring.enqueue(elem)
    }
    def dequeue(): EventQueue[T] = {
      val value = monitor.synchronized {
        if (ring.isEmpty) return this
        else ring.dequeue()
      }
      reactAll(value)
      this
    }
    def isEmpty = monitor.synchronized { ring.isEmpty }
  }

}
