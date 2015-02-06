package scala.reactive



import scala.collection._



/** An interface that describes event sinks.
 *  Event sinks are created by `onX` methods, and persisted in the isolate until
 *  unsubscribed or unreacted.
 */
trait EventSink {

  def registerEventSink() {
    Iso.selfIso.get match {
      case null =>
        EventSink.globalEventSinks += this
      case iso =>
        iso.eventSinks += this
    }
  }

  def unregisterEventSink() {
    Iso.selfIso.get match {
      case null =>
        EventSink.globalEventSinks -= this
      case iso =>
        iso.eventSinks -= this
    }
  }

  def liftSubscription(s: Reactive.Subscription) = {
    Reactive.Subscription {
      unregisterEventSink()
      s.unsubscribe()
    }
  }

}


object EventSink {

  /** A set of global event sinks. */
  private[reactive] val globalEventSinks = mutable.Set[EventSink]()

}
