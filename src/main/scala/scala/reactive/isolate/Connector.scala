package scala.reactive
package isolate






/** An entity that encapsulates an event queue and the corresponding channel.
 *
 *  Uses the specified isolate frame and its isolate system to instantiate a channel,
 *  and associate it with the event queue -- events arriving on the channel are sent to the event queue.
 *  A scheduler eventually uses the dequeuer to propagate events in the isolate.
 *  
 *  @tparam T            the type of the events in this connector
 *  @param frame         the isolate frame
 *  @param queue         the event queue
 */
class Connector[@spec(Int, Long, Double) T](
  private[reactive] val frame: IsolateFrame,
  private[reactive] val queue: EventQueue[T]
) {
  @volatile private[reactive] var dequeuer: Dequeuer[T] = _
  @volatile private[reactive] var reactor: Reactor[T] = _
  @volatile private[reactive] var chan: Channel[T] = _

  private[reactive] def init(dummy: Connector[T]) {
    dequeuer = queue.foreach(frame)
    reactor = new Connector.Reactor(this)
    chan = frame.isolateSystem.newChannel(reactor)
  }

  init(this)

  /** The event stream associated with this connector.
   *
   *  @return            the event stream
   */
  def events: Reactive[T] = dequeuer.events

  /** The channel associated with this connector.
   *
   *  @return            the channel
   */
  def channel: Channel[T] = chan

}


object Connector {
  /** Reactor implementation which forwards the events to the connector's event queue,
   *  and notifies the multiplexer.
   */
  class Reactor[@spec(Int, Long, Double) T](
    val connector: Connector[T]
  ) extends scala.reactive.Reactor[T] {
    def react(event: T) = {
      connector.queue enqueue event
      connector.frame.multiplexer.reacted(connector)
    }
    def unreact() = {
      connector.frame.multiplexer.unreacted(connector)
      connector.frame.apply()
    }
  }
}
