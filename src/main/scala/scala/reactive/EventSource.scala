package scala.reactive






/** An interface that describes event sources.
 *
 *  Automatically installs itself into the `EventSource` set of the enclosing isolate.
 *  If the event source object is created outside of the isolate, it is not registered
 *  by any isolates.
 */
trait EventSource {
  
  Isolate.selfOrNull[Isolate[_]] match {
    case null =>
    case iso  => iso.eventSources += this
  }

  /** Closes the event source, disallowing it from emitting any further events.
   */
  def close(): Unit

}
