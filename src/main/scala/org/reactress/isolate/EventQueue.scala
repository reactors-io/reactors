package org.reactress
package isolate



import scala.annotation._
import scala.collection._



trait EventQueue[@spec(Int, Long, Double) Q]
extends Enqueuer[Q] {
  def foreach[@spec(Int, Long, Double) T](f: IsolateFrame[T, Q])(implicit scheduler: Scheduler): Dequeuer[Q]
  def isEmpty: Boolean
  def nonEmpty = !isEmpty
}


object EventQueue {

  class SingleSubscriberSyncedUnrolledRing[@spec(Int, Long, Double) Q: Arrayable](val monitor: util.Monitor)
  extends EventQueue[Q] {
    private[reactress] val ring = new util.UnrolledRing[Q]

    private[reactress] var listener: IsolateFrame[_, Q] = _

    def +=(elem: Q) = {
      val l = monitor.synchronized {
        ring.enqueue(elem)
        listener
      }
      wakeAll(l)
    }

    private def wakeAll(frame: IsolateFrame[_, Q]): Unit = {
      frame.wake()
    }

    def foreach[@spec(Int, Long, Double) T](f: IsolateFrame[T, Q])(implicit scheduler: Scheduler) = monitor.synchronized {
      val dequeuer = new SingleSubscriberSyncedUnrolledRingDequeuer(this)
      listener = f
      dequeuer
    }

    def isEmpty = monitor.synchronized { ring.isEmpty }

    def dequeue(): Q = monitor.synchronized {
      ring.dequeue()
    }

    def enqueueIfEmpty(v: Q) = monitor.synchronized {
      if (ring.isEmpty) ring.enqueue(v)
    }
  }

  class SingleSubscriberSyncedUnrolledRingDequeuer[@spec(Int, Long, Double) Q]
    (q: SingleSubscriberSyncedUnrolledRing[Q])
  extends Dequeuer[Q] {
    def dequeue() = q.dequeue()
    def isEmpty = q.isEmpty
  }

}
