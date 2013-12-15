package org.reactress



import collection._



trait ReactMutable {

  def bindSubscription(s: Reactive.Subscription): Reactive.Subscription = s

  def onMutated(): Unit = {}

}