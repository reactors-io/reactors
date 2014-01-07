package org.reactress
package container



import scala.collection._



abstract class SubscriptionSet {

  def +=(s: Reactive.Subscription): Unit

  def -=(s: Reactive.Subscription): Unit

  def unsubscribe(): Unit

}


object SubscriptionSet {

  def apply() = new SubscriptionSet {
    private var set = mutable.Set[Reactive.Subscription]()
    def +=(s: Reactive.Subscription) = set += s
    def -=(s: Reactive.Subscription) = set -= s
    def unsubscribe() {
      val oldset = set
      set = mutable.Set()
      for (s <- oldset) s.unsubscribe()
      oldset.clear()
    }
  }

}
