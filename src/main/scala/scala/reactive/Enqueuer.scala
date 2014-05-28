package scala.reactive






/** An interface for enqueuing events.
 *
 *  Every isolate provides an interface for enqueuing events later,
 *  i.e. during the next event propagation.
 *  The `Enqueuer` is a thread-safe interface.
 *
 *  @tparam T           the type of events enqueued in this enqueuer
 */
trait Enqueuer[@spec(Int, Long, Double) -T] {
  
  /** Atomically enqueue an element.
   *
   *  @param event      the event to enqueue
   */
  def enqueue(event: T): Unit
  
  /** Atomically enqueues an element if the queue is empty.
   *
   *  @param event      the event to enqueue
   */
  def enqueueIfEmpty(event: T): Unit

}
