package org.reactress
package isolate



import scala.annotation._
import scala.collection._



trait EventQueue[@spec(Int, Long, Double) Q]
extends Enqueuer[Q] {
  def foreach(f: IsolateFrame[_, Q])(implicit scheduler: Scheduler): Unit
  def isEmpty: Boolean
  def nonEmpty = !isEmpty
}


object EventQueue {

  class SingleSubscriberSyncedUnrolledRing[@spec(Int, Long, Double) Q: Arrayable](val monitor: util.Monitor)
  extends EventQueue[Q] {
    private[reactress] val ring = new util.UnrolledRing[Q]

    private[reactress] var listener: (Dequeuer[Q], IsolateFrame[_, Q]) = _

    def +=(elem: Q) = {
      val l = monitor.synchronized {
        ring.enqueue(elem)
        listener
      }
      wakeAll(l)
    }

    private def wakeAll(lis: (Dequeuer[Q], IsolateFrame[_, Q])): Unit = {
      val deq = lis._1
      val frame = lis._2
      @tailrec def wake(): Unit = if (!frame.isOwned) {
        if (frame.tryOwn()) frame.scheduler.schedule(frame, deq)
        else wake()
      }
      wake()
    }

    def foreach(f: IsolateFrame[_, Q])(implicit scheduler: Scheduler) = monitor.synchronized {
      val dequeuer = new SingleSubscriberSyncedUnrolledRingDequeuer(this)
      listener = (dequeuer, f)
    }

    def isEmpty = monitor.synchronized { ring.isEmpty }

    def dequeue(): Q = monitor.synchronized {
      ring.dequeue()
    }

    def dequeueEnqueueIfEmpty(v: Q): Q = monitor.synchronized {
      val r = ring.dequeue()
      if (ring.isEmpty) ring.enqueue(v)
      r
    }
  }

  class SingleSubscriberSyncedUnrolledRingDequeuer[@spec(Int, Long, Double) Q]
    (q: SingleSubscriberSyncedUnrolledRing[Q])
  extends Dequeuer[Q] {
    def dequeue() = q.dequeue()
    def isEmpty = q.isEmpty
    def dequeueEnqueueIfEmpty(v: Q) = q.dequeueEnqueueIfEmpty(v)
  }

}
