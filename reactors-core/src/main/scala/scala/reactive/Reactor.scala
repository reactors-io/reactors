package scala.reactive






/** An object that can act upon an event or be signalled that
 *  there will be no more events.
 *
 *  @tparam T        type of events the observer responds to
 */
trait Reactor[@spec(Int, Long, Double) -T] {

  /** Called by an event stream when an event `value` is produced.
   * 
   *  @param value   the event passed to the reactor
   */
  def react(value: T): Unit

  /** Called by an event stream when an exception is produced.
   *
   *  @param t       the exception passed to the reactor
   */
  def except(t: Throwable): Unit

  /** Called by an event stream when there will be no further updates.
   */
  def unreact(): Unit
}


object Reactor {

  class EventSink[@spec(Int, Long, Double) T]
    (val underlying: Reactor[T], val canLeak: CanLeak)
  extends Reactor[T] with Events.ProxySubscription {
    var subscription = Events.Subscription.empty

    def react(value: T) = {
      underlying.react(value)
    }

    def except(t: Throwable) = {
      underlying.except(t)
    }

    def unreact() = {
      Events.Subscription.unregisterLeakySubscription(canLeak, subscription)
      underlying.unreact()
    }
  }

}
