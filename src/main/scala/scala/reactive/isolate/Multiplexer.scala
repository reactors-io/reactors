package scala.reactive
package isolate



import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec
import scala.collection._



/** A thread-safe collection containing connectors.
 *
 *  A multiplexer must always be *consistently used*.
 *  *Consistency* means that the `+=` and `dequeueEvent` methods
 *  are never called during the execution of the `isTerminated`, `areEmpty` and `totalSize`.
 *  It is the task of the scheduler/isolate system combination to use the multiplexer consistently
 *  (that is, only calls the above-mentioned methods from within isolate context, ensuring their serializibility).
 *  By contrast, methods `reacted` and `unreacted` (and `enqueue` on associated event queues)
 *  can be called whenever by whichever thread.
 */
trait Multiplexer {

  /** Quiescently checks if all the event queues associated with the connectors are empty.
   *
   *  Assumes consistent use.
   *  Under this assumption, the method is quiescently consistent.
   *  Moreover, if it returns `false`, then the execution is atomic.
   *  
   *  @return             `true` if all the event queues are empty, `false` otherwise
   */
  def areEmpty: Boolean

  /** *Atomically* checks if all the channels are terminated, and all the event queues are empty.
   *
   *  Assumes consistent use.
   *  Under this assumption, the method is always atomic.
   *  
   *  @return             `true` if the multiplexer's connectors are terminated, `false` otherwise
   */
  def isTerminated: Boolean

  /** Under-estimated total size of the event queues associated with the connectors.
   *
   *  Assumes consistent use.
   *  Under this assumption, the method is quiescently consistent,
   *  and under-estimates the actual number of events in all the event queues.
   *  
   *  @return             the estimated event queue size
   */
  def totalSize: Int

  /** Releases an event from some event queue according to some strategy.
   *
   *  At least one event queue must non-empty.
   */
  def dequeueEvent(): Unit

  /** Adds a connector to this multiplexer.
   *  
   *  @param connector    the connector to add
   */
  def +=(connector: Connector[_]): Unit

  /** Signals that an event from the channel has been put to the event queue.
   *  
   *  @param connector    the connector that reacted
   */
  def reacted(connector: Connector[_]): Unit

  /** Signals that a channel associated with the connector has terminated, and will not add more events to the event queue.
   *  
   *  @param connector    the connector that unreacted
   */
  def unreacted(connector: Connector[_]): Unit

}


object Multiplexer {

  /** The default multiplexer implementation.
   *
   *  Heuristically picks the connector with most events when `dequeueEvent` is called.
   *  The connector with the largest event queue is eventually chosen.
   *  Simultaneously, the multiplexer strives to be fair and eventually choose every connector.
   *
   *  In this implementation, the `totalSize` method is O(n).
   */
  class Default extends Multiplexer {
    private val updateFrequency = 200
    private var descriptors = immutable.TreeSet[Default.Desc]()
    @volatile private var first: Connector[_] = null

    def areEmpty = this.synchronized {
      check(first, false)
      first == null || first.dequeuer.isEmpty
    }

    def isTerminated = this.synchronized {
      check(first, false)
      descriptors.isEmpty
    }

    def totalSize = this.synchronized {
      var sz = 0
      val it = descriptors.iterator
      while (it.hasNext) sz += it.next().connector.dequeuer.size
      sz
    }

    private def bookkeep(connector: Connector[_], d: Default.Desc) {
      this.synchronized {
        descriptors -= d
        d.priority = connector.dequeuer.size - updateFrequency
        if (!connector.isTerminated) descriptors += d
        if (descriptors.isEmpty) first = null
        else first = descriptors.firstKey.connector
      }
    }

    private def desc(c: Connector[_]): Default.Desc = c.multiplexerInfo match {
      case d: Default.Desc => d
    }

    @tailrec private def check(connector: Connector[_], bumpUp: Boolean): Unit = if (connector != null) {
      val d = desc(connector)
      val actionCount = if (bumpUp) d.accessCounter.incrementAndGet() else d.accessCounter.get
      if (actionCount % updateFrequency == 0 || connector.dequeuer.isEmpty) {
        bookkeep(connector, d)
        check(first, false)
      }
    }

    @tailrec final def dequeueEvent() = {
      val connector = /*READ*/first
      if (connector.dequeuer.isEmpty) {
        bookkeep(connector, desc(connector))
        dequeueEvent()
      } else {
        connector.dequeuer.dequeue()
        check(connector, true)
      }
    }

    def +=(connector: Connector[_]) = this.synchronized {
      val d = new Default.Desc(connector, 0)
      connector.multiplexerInfo = d
      descriptors += d
      if (first == null) first = connector
    }

    def reacted(connector: Connector[_]) = check(connector, true)

    def unreacted(connector: Connector[_]) = if (connector.isTerminated) check(connector, true)
  }

  object Default {
    class Desc(val connector: Connector[_], var priority: Long) {
      val accessCounter = new AtomicLong(0)
    }

    object Desc {
      implicit val ordering: Ordering[Desc] = new Ordering[Desc] {
        def compare(x: Desc, y: Desc): Int = {
          if (x.priority > y.priority) -1
          else if (x.priority < y.priority) 1
          else 0
        }
      }
    }
  }

}
