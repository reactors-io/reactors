package scala.reactive
package isolate



import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import scala.annotation.tailrec
import scala.collection._



/** A thread-safe collection containing connectors.
 *
 *  A multiplexer must always be *consistently used*.
 *  *Consistency* means that the `+=` and `dequeueEvent` methods
 *  are never called during the execution of the `isTerminated`, `areEmpty` and
 *  `totalSize`.
 *  It is the task of the scheduler/isolate system combination to use the
 *  multiplexer consistently
 *  (that is, only calls the above-mentioned methods from within isolate
 *  context, ensuring their serializibility).
 *  By contrast, methods `reacted` and `unreacted` (and `enqueue` on associated
 *  event queues) can be called whenever by whichever thread.
 */
trait Multiplexer {

  /** Quiescently checks if all the event queues associated with the connectors
   *  are empty.
   *
   *  Assumes consistent use.
   *  Under this assumption, the method is quiescently consistent.
   *  Moreover, if it returns `false`, then the execution is atomic.
   *  
   *  @return             `true` if all the event queues are empty, `false`
   *                      otherwise
   */
  def areEmpty: Boolean

  /** *Atomically* checks if all the channels are terminated, and all the event
   *  queues are empty.
   *
   *  Assumes consistent use.
   *  Under this assumption, the method is always atomic.
   *  
   *  @return             `true` if the multiplexer's connectors are terminated,
   *                      `false` otherwise
   */
  def isTerminated: Boolean

  /** Under-estimated total size of the event queues associated with the
   *  connectors.
   *
   *  Assumes consistent use.
   *  Under this assumption, the method is quiescently consistent,
   *  and under-estimates the actual number of events in all the event queues.
   *  
   *  @return             the estimated event queue size
   */
  def totalSize: Int

  /** Releases an event from some event queue according to some strategy.
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

  /** Signals that a channel associated with the connector has terminated,
   *  and will not add more events to the event queue.
   *  
   *  @param connector    the connector that unreacted
   */
  def unreacted(connector: Connector[_]): Unit

}


object Multiplexer {

  /** The default multiplexer implementation.
   *
   *  Heuristically picks the connector with most events when `dequeueEvent` is
   *  called.
   *  The connector with the largest event queue is eventually chosen and
   *  flushed.
   *  Simultaneously, the multiplexer strives to be fair and eventually choose
   *  every connector.
   *
   *  In this implementation, the `totalSize` method is `O(n)`, where `n` is the
   *  **number of event queues**.
   */
  class Default extends Multiplexer {
    private val minUpdateFrequency = 200
    private var descriptors = mutable.ArrayBuffer[Connector[_]]()
    private var liveCount = 0
    private var pos = 0
    @volatile private var current: Connector[_] = null

    def areEmpty = this.synchronized {
      check(current, false)
      current == null || current.dequeuer.isEmpty
    }

    def isTerminated = this.synchronized {
      check(current, false)
      liveCount == 0
    }

    def totalSize = this.synchronized {
      var sz = 0
      var i = 0
      while (i < descriptors.length) {
        sz += descriptors(i).dequeuer.size
        i += 1
      }
      sz
    }

    private def desc(c: Connector[_]): Default.Desc = c.multiplexerInfo match {
      case d: Default.Desc => d
    }

    private def addConnector(connector: Connector[_]) {
      if (!connector.isDaemon) liveCount += 1
      descriptors += connector
    }

    private def deleteCurrentConnector() {
      if (!current.isDaemon) liveCount -= 1
      val lastPos = descriptors.length - 1
      descriptors(pos) = descriptors(lastPos)
      descriptors.remove(lastPos)
      if (pos == lastPos) pos = 0
    }

    private def findNonEmpty() {
      var attemptsLeft = descriptors.length
      while (attemptsLeft > 0 && descriptors(pos).dequeuer.isEmpty) {
        current = descriptors(pos)
        if (current.isTerminated) deleteCurrentConnector()
        pos = (pos + 1) % descriptors.length
        attemptsLeft -= 1
      }
    }

    private def bookkeep() = this.synchronized {
      val connector = /*READ*/current
      if (connector != null) {
        if (connector.isTerminated) deleteCurrentConnector()

        findNonEmpty()

        if (descriptors.isEmpty) current = null
        else {
          val newCurrent = descriptors(pos)
          val newCount = math.max(minUpdateFrequency, newCurrent.dequeuer.size)
          desc(newCurrent).countdown.set(newCount)
          current = newCurrent
        }
      }
    }

    private def check(connector: Connector[_], bumpDown: Boolean): Unit = {
      if (connector != null) {
        val d = desc(connector)
        val count =
          if (bumpDown) d.countdown.decrementAndGet() else d.countdown.get
        if (count <= 0 || connector.dequeuer.isEmpty) bookkeep()
      }
    }

    @tailrec final def dequeueEvent() = {
      val connector = /*READ*/current
      if (connector != null) {
        if (connector.dequeuer.isEmpty) {
          bookkeep()
          val nconnector = /*READ*/current
          if (nconnector != null && nconnector.dequeuer.nonEmpty) dequeueEvent()
        } else {
          connector.dequeuer.dequeue()
          check(connector, true)
        }
      }
    }

    def +=(connector: Connector[_]) = this.synchronized {
      val d = new Default.Desc(connector)
      connector.multiplexerInfo = d
      addConnector(connector)
      if (current == null) current = connector
    }

    def reacted(connector: Connector[_]) = {}

    def unreacted(connector: Connector[_]) = {}
  }

  object Default {
    class Desc(val connector: Connector[_]) {
      val countdown = new AtomicInteger(0)
    }
  }

}
