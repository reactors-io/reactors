package scala.reactive
package isolate



import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap



/** A thread-safe collection containing connectors.
 *
 *  A multiplexer must always be *consistently used*.
 *  *Consistency* means that the `+=` and `dequeueEvent` methods
 *  are never called during the execution of the `isTerminated`, `areEmpty` and `totalSize`.
 *  It is the task of the scheduler/isolate system combination to use the multiplexer consistently
 *  (that is, only calls the above-mentioned methods from within isolate context, ensuring their serializibility).
 *  By contrast, methods `reacted` and `unreacted` (and `enqueue` on associated event queues)
 *  can be called whenever by whichever threads.
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
   *  Heuristically picks the connector with most events
   *  when `dequeueEvent` is called.
   */
  class Default extends Multiplexer {
    val updateFrequency = 50
    val connectors = new ConcurrentHashMap[Connector[_], Default.Desc]

    def areEmpty = ???

    def isTerminated = ???

    def totalSize = {
      ???
    }

    def dequeueEvent() = {
      val connector = ???

      connector.multiplexerInfo match {
        case d: Default.Desc =>
          ???
      }
    }

    def +=(connector: Connector[_]) = {
      val d = new Default.Desc
      connector.multiplexerInfo = d
      connectors.put(connector, d)
    }

    def reacted(connector: Connector[_]) = {
      connector.multiplexerInfo match {
        case d: Default.Desc =>
          d.messageEstimate.incrementAndGet()
          if (d.accessCounter.incrementAndGet() % updateFrequency == 0) {
            ???
          }
      }
    }

    def unreacted(connector: Connector[_]) = {}
  }

  object Default {
    class Desc {
      val accessCounter = new AtomicLong(0)
      val messageEstimate = new AtomicInteger(0)
    }
  }

}
