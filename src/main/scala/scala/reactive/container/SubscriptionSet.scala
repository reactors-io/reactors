package scala.reactive
package container



import scala.collection._



abstract class SubscriptionSet {

  def +=(s: Events.Subscription): Unit

  def -=(s: Events.Subscription): Unit

  def unsubscribe(): Unit

}


object SubscriptionSet {

  def apply() = new SubscriptionSet {
    private var set = mutable.Set[Events.Subscription]()
    def +=(s: Events.Subscription) = set += s
    def -=(s: Events.Subscription) = set -= s
    def unsubscribe() {
      val oldset = set
      set = mutable.Set()
      for (s <- oldset) s.unsubscribe()
      oldset.clear()
    }
  }

}
