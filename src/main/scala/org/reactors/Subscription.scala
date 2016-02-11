package org.reactors



import scala.collection._



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


/** Default subscription implementations.
 */
object Subscription {

  /** Does not unsubscribe from anything. */
  val empty = new Subscription {
    def unsubscribe() = {}
  }

  class Composite(ss: Subscription*) extends Subscription {
    def unsubscribe() {
      for (s <- ss) s.unsubscribe()
    }
  }

  /** Forwards `unsubscribe` to another subscription.
   */
  trait Proxy extends Subscription {
    def subscription: Subscription
    def unsubscribe() {
      subscription.unsubscribe()
    }
  }

  /** A mutable collection of subscriptions, which is itself a subscription.
   *
   *  When unsubscribed from, all containing subscriptions are unsubscribed.
   *  Subsequently added subscriptions are automatically unsubscribed.
   */
  class Collection extends Subscription {
    private var done = false
    private val set = mutable.Set[Subscription]()
    def unsubscribe() {
      done = true
      for (s <- set) s.unsubscribe()
    }
    def addAndGet(s: Subscription): Subscription = {
      if (done) {
        s.unsubscribe()
        Subscription.empty
      } else {
        val ns = new Subscription {
          override def unsubscribe() {
            s.unsubscribe()
            set -= this
          }
        }
        set += ns
        ns
      }
    }
    def isEmpty: Boolean = set.isEmpty
  }

}
