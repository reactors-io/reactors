package scala.reactive






/** The dequeuer instance offers a unique dequeuing interfaces for the queue's elements.
 *
 *  An event queue can have many dequeuers.
 *  An element is removed from a queue only after all its dequeuers have dequeued it
 *  by calling `dequeue`.
 *  This allows tracking multiple event queue subscribers.
 *  In essence, a `Dequeuer` is an iterator that is internally used by the
 *  queue implementation to drop elements that are no longer used.
 *
 *  When `dequeue` is called, an event is not returned.
 *  Instead, it is emitted on the `events` event stream associated with this dequeuer.
 *
 *  A dequeuer is used internally by isolate schedulers.
 *  
 *  @tparam T        the type of events stored in the queue this dequeuer belongs to
 */
trait Dequeuer[@spec(Int, Long, Double) T] {
  /** Returns the next element.
   */
  def dequeue(): Unit

  /** The event stream associated with this dequeuer,
   *  which emits an event each time `dequeue` is invoked.
   *
   *  @return        the event stream of this dequeuer.
   */
  def events: Events[T]

  /** Returns the number of elements left in the dequeuer.
   *
   *  @return        the number of elements 
   */
  def size: Int

  /** Tests if the dequeuer is empty.
   *
   *  @return        `true` if the dequeuer returned all the currently available elements
   */
  def isEmpty: Boolean

  /** Tests if the dequeuer is non-empty.
   *
   *  @return        `false` if the dequeuer returned all the currently available elements
   */
  def nonEmpty = !isEmpty
}
