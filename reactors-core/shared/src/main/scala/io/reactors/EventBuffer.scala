package io.reactors



import io.reactors.common.UnrolledRing



/** An event buffer that is simultaneously an event stream.
 *
 *  Events are stored into the buffer with the `enqueue` method.
 *  Events are dequeued and emitted with the `dequeue` method.
 *  Calling `unsubscribe` unreacts.
 *
 *  '''Note:''' this class can only be used inside a single reactor, and is not meant
 *  to be shared across multiple threads.
 *
 *  @tparam T       type of the events in this event stream
 */
class EventBuffer[@spec(Int, Long, Double) T: Arrayable]
extends Events.Push[T] with Events[T] with Subscription.Proxy {
  private val buffer = new UnrolledRing[T]
  private val availabilityCell = new RCell[Boolean](false)
  private[reactors] var innerSubscription = Subscription.empty

  /** The subscription associated with this event buffer.
   */
  val subscription: Subscription = new Subscription.Composite(
    innerSubscription,
    new Subscription {
      override def unsubscribe(): Unit = unreactAll()
    }
  )

  /** Enqueues an event.
   */
  def enqueue(x: T): Unit = {
    try buffer.enqueue(x)
    finally availabilityCell := true
  }

  /** Dequeues a previously enqueued event.
   */
  def dequeue(): Unit = {
    val x = buffer.dequeue()
    try reactAll(x, null)
    finally if (buffer.isEmpty) {
      availabilityCell := false
    }
  }

  /** A signal that designates whether the event buffer has events to dequeue.
   */
  def available: Signal[Boolean] = availabilityCell

  /** Size of the event buffer.
   */
  def size: Int = buffer.size

  /** Returns `true` iff the buffer is empty.
   */
  def isEmpty: Boolean = buffer.isEmpty

  /** Returns `true` iff the buffer is non-empty.
   */
  def nonEmpty: Boolean = buffer.nonEmpty
}
