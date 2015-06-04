package scala.reactive



import scala.annotation._
import scala.collection._
import scala.reactive.isolate.IsoFrame



/** Defines event queue.
 * 
 *  Event queues are entities that buffer events sent to an isolate.
 *  
 *  @tparam Q      type of events in this event queue
 */
trait EventQueue[@spec(Int, Long, Double) Q]
extends Enqueuer[Q] {

  /** Register a listener for the events in the event queue.
   *  
   *  Will call the specified function when events arrive.
   *
   *  @param f     a callback for the events
   *  @return      the dequeuer object used to dequeue events
   */
  def foreach(f: () => Unit): Dequeuer[Q]

  /** The size of the event queue.
   *
   *  Should at least be quiescently consistent.
   *  
   *  @return      the size of the event queue, or an approximation if there are concurrent updates
   */
  def size: Int

  /** Checks whether the event queue is empty.
   *
   *  This method is linearizable.
   *  
   *  @return      `true` if the event queue is empty
   */
  def isEmpty: Boolean

  /** Checks whether the event queue is non-empty.
   *
   *  This method is linearizable.
   *  
   *  @return      `true` if the event queue is non-empty
   */
  def nonEmpty = !isEmpty
}


/** Event queue factory methods and standard implementations.
 */
object EventQueue {

  /** Object that creates event queues holding events of a desired type.
   */
  trait Factory {
    def create[@specialized(Int, Long, Double) Q: Arrayable]: EventQueue[Q]
  }

  /** Event queue based on monitor locks and an unrolled ring data structure.
   */
  class SingleSubscriberSyncedUnrolledRing[@spec(Int, Long, Double) Q: Arrayable](val monitor: util.Monitor)
  extends EventQueue[Q] {
    private[reactive] val ring = new core.UnrolledRing[Q]

    private[reactive] var listener: () => Unit = null

    def enqueue(elem: Q) = {
      val l = monitor.synchronized {
        ring.enqueue(elem)
        listener
      }
      wakeAll(l)
    }

    private def wakeAll(l: () => Unit): Unit = {
      if (l != null) l.apply()
    }

    def foreach(f: () => Unit) = monitor.synchronized {
      val dequeuer = new SingleSubscriberSyncedUnrolledRing.Dequeuer(this)
      if (listener != null) sys.error("Event queue supports only a single subscriber.")
      else listener = f
      dequeuer
    }

    def size = monitor.synchronized {
      ring.size
    }

    def isEmpty = monitor.synchronized {
      ring.isEmpty
    }

    def dequeue() = monitor.synchronized {
      ring.dequeue()
    }

    def enqueueIfEmpty(v: Q) = monitor.synchronized {
      if (ring.isEmpty) ring.enqueue(v)
    }
  }

  object SingleSubscriberSyncedUnrolledRing {
    class Dequeuer[@spec(Int, Long, Double) Q](q: SingleSubscriberSyncedUnrolledRing[Q])
    extends scala.reactive.Dequeuer[Q] {
      val events = new Events.Emitter[Q]
      def dequeue() = events.react(q.dequeue())
      def isEmpty = q.isEmpty
      def size = q.size
    }

    class Factory extends EventQueue.Factory {
      def create[@specialized(Int, Long, Double) Q: Arrayable] =
        new SingleSubscriberSyncedUnrolledRing[Q](new util.Monitor)
    }

    val factory = new Factory
  }

  /** An event queue implementation that categorically loses all events.
   */
  class DevNull[@spec(Int, Long, Double) Q: Arrayable]
  extends EventQueue[Q] {
    def enqueue(x: Q) {}
    def enqueueIfEmpty(x: Q) {}
    def foreach(f: () => Unit) = new DevNull.Dequeuer[Q]
    def size = 0
    def isEmpty = true
  }

  object DevNull {
    class Dequeuer[Q] extends scala.reactive.Dequeuer[Q] {
      def dequeue() = {}
      def events = Events.Never[Q]
      def isEmpty = true
      def size = 0
    }

    class Factory extends EventQueue.Factory {
      def create[@specialized(Int, Long, Double) Q: Arrayable] = new DevNull[Q]
    }

    val factory = new Factory
  }

}
