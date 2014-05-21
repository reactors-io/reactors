package org.reactress






/** The dequeuer instance offers a unique dequeuing interfaces for the queue's elements.
 *
 *  An event queue can have many dequeuers.
 *  An element is removed from a queue only after all its dequeuers have dequeued it
 *  by calling `dequeue`.
 *  This allows tracking multiple event queue subscribers.
 *  In essence, a `Dequeuer` is an iterator that is internally used by the
 *  queue implementation to drop elements that are no longer used.
 *
 *  A dequeuer is used internally by isolate schedulers.
 *  
 *  @tparam T        the type of events stored in the queue this dequeuer belongs to
 */
trait Dequeuer[@spec(Int, Long, Double) T] {
  /** Returns the next element.
   *
   *  @return        the next event in the event queue
   */
  def dequeue(): T

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
