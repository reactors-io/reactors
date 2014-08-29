package scala.reactive
package isolate



import java.util.concurrent.atomic.AtomicLong



/** A thread-safe collection containing connectors.
 *
 *  A multiplexer must always be *consistently used*.
 *  Here, *consistency* means that the `+=` and `dequeueEvent` methods
 *  are never called during the execution of the `isTerminated`, `areEmpty` and `totalSize`.
 *  This is ensured by any scheduler/isolate system combination which uses the multiplexer consistently
 *  (that is, only calls the above-mentioned methods from within isolate context, ensuring their serializibility).
 *  By contrast, methods `reacted` and `unreacted` (and `enqueue` on event queues) can be called whenever by whichever threads.
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
    def areEmpty = ???

    def isTerminated = ???

    def totalSize = ???

    def dequeueEvent() = ???

    def +=(connector: Connector[_]) = ???

    def reacted(connector: Connector[_]) = ???

    def unreacted(connector: Connector[_]) = ???
  }

}
