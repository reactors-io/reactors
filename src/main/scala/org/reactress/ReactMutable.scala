package org.reactress



import collection._



trait ReactMutable {

  def bindSubscription(s: Reactive.Subscription): Reactive.Subscription = s

  def onMutated(): Unit = {}

}


object ReactMutable {

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