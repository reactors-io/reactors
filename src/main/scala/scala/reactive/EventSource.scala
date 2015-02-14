package scala.reactive






/** An interface that describes event sources.
 *
 *  Automatically installs itself into the `EventSource` set of the enclosing
 *  isolate.
 *  If the event source object is created outside of the isolate, it is not
 *  registered by any isolates.
 */
trait EventSource {
  
  private var sub: Reactive.Subscription = _

  private def initEventSource() {
    sub = Iso.selfOrNull[Iso[_]] match {
      case null =>
        Reactive.Subscription.empty
      case iso  =>
        iso.eventSources += this
        ultimately { iso.eventSources -= this }
    }
  }

  initEventSource()

  /** Closes the event source, disallowing it from emitting any further events.
   */
  def unreact(): Unit

  /** Invoked when the event source is closed.
   */
  def ultimately(reactor: =>Unit): Reactive.Subscription

}
