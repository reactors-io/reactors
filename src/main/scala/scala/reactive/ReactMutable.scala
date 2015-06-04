package scala.reactive



import collection._



/** Describes event streams that are either mutable, or contain and emit events that
 *  are themselves mutable.
 */
trait ReactMutable {

  /** Called internally - binds a subscription to this `ReactMutable`.
   */
  def bindSubscription(s: Events.Subscription): Events.Subscription = s

  /** Called internally - when the `ReactMutable` or its internal value has
   *  been mutated.
   */
  def mutation(): Unit

  /** Called when an exception occurs during mutation.
   *  Some `ReactMutable`s may propagate the exception further.
   */
  def exception(t: Throwable): Unit

}


/** Helper traits and implementations for `ReactMutable`s.
 */
object ReactMutable {

  /** Used internally - holds a list of subscriptions bound to this `ReactMutable`.
   */
  trait Subscriptions extends ReactMutable {
    val subscriptions = mutable.Set[Events.Subscription]()

    def clearSubscriptions() {
      for (s <- subscriptions) s.unsubscribe()
      subscriptions.clear()
    }
    
    override def bindSubscription(s: Events.Subscription) = new Events.Subscription {
      subscriptions += this
      def unsubscribe() {
        s.unsubscribe()
        subscriptions -= this
      }
    }
  }

}
