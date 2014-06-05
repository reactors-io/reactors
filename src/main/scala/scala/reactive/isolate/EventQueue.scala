package scala.reactive
package isolate



import scala.annotation._
import scala.collection._



/** Defines event queue.
 * 
 *  Event queues are entities that buffer events sent to an isolate.
 *  
 *  @tparam Q      type of events in this event queue
 */
trait EventQueue[@spec(Int, Long, Double) Q]
extends Enqueuer[Q] {
  def foreach(f: IsolateFrame[Q])(implicit scheduler: Scheduler): Dequeuer[Q]
  def size: Int
  def isEmpty: Boolean
  def nonEmpty = !isEmpty
}


object EventQueue {

  class SingleSubscriberSyncedUnrolledRing[@spec(Int, Long, Double) Q: Arrayable](val monitor: util.Monitor)
  extends EventQueue[Q] {
    private[reactive] val ring = new core.UnrolledRing[Q]

    private[reactive] var listener: IsolateFrame[Q] = _

    def enqueue(elem: Q) = {
      val l = monitor.synchronized {
        ring.enqueue(elem)
        listener
      }
      wakeAll(l)
    }

    private def wakeAll(frame: IsolateFrame[Q]): Unit = {
      frame.wake()
    }

    def foreach(f: IsolateFrame[Q])(implicit scheduler: Scheduler) = monitor.synchronized {
      val dequeuer = new SingleSubscriberSyncedUnrolledRingDequeuer(this)
      listener = f
      dequeuer
    }

    def size = monitor.synchronized {
      ring.size
    }

    def isEmpty = monitor.synchronized {
      ring.isEmpty
    }

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
