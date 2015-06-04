package scala.reactive






/** An interface that describes event sinks.
 *  Event sinks are created by `onX` methods, and persisted in the isolate until
 *  unsubscribed or unreacted.
 */
trait EventSink {

  def registerEventSink(canLeak: CanLeak) {
    canLeak.eventSinks += this
  }

  def unregisterEventSink(canLeak: CanLeak) {
    canLeak.eventSinks -= this
  }

  def liftSubscription(s: Events.Subscription, canLeak: CanLeak) = {
    Events.Subscription {
      unregisterEventSink(canLeak)
      s.unsubscribe()
    }
  }

}


object EventSink {

}
