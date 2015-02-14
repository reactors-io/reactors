package scala.reactive



import collection._



/** Describes reactives that are either mutable, or contain and emit events that
 *  are themselves mutable.
 */
trait ReactMutable {

  /** Called internally - binds a subscription to this reactive mutable value.
   */
  def bindSubscription(s: Reactive.Subscription): Reactive.Subscription = s

  /** Called internally - when the mutable reactive or its internal value has
   *  been mutated.
   */
  def react(): Unit

  def except(t: Throwable): Unit

}


/** Helper traits and implementations for reactive mutables.
 */
object ReactMutable {

  /** Used internally - holds a list of subscriptions bound to this reactive mutable.
   */
  trait Subscriptions extends ReactMutable {
    val subscriptions = mutable.Set[Reactive.Subscription]()

    def clearSubscriptions() {
      for (s <- subscriptions) s.unsubscribe()
      subscriptions.clear()
    }
    
    override def bindSubscription(s: Reactive.Subscription) = new Reactive.Subscription {
      subscriptions += this
      def unsubscribe() {
        s.unsubscribe()
        subscriptions -= this
      }
    }
  }

}
