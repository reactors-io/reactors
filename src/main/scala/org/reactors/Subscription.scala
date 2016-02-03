package org.reactors






/** A subscription to a certain kind of event, event processing or computation.
 *
 *  Calling `unsubscribe` on the subscription causes the events to no longer be
 *  propagated to this subscription, or some computation to cease.
 *
 *  Unsubscribing is idempotent -- calling `unsubscribe` second time does nothing.
 */
trait Subscription {

  /** Stops event propagation on the corresponding event stream.
   */
  def unsubscribe(): Unit  

}
