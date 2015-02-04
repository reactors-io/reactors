package scala.reactive



import scala.collection._



/** An interface that describes event sinks.
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

  def deregisterEventSink() {
    Iso.selfIso.get match {
      case null =>
        EventSink.globalEventSinks -= this
      case iso =>
        iso.eventSinks -= this
    }
  }

}


object EventSink {

  val globalEventSinks = mutable.Set[EventSink]()

}
