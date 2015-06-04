package scala.reactive






/** An object that can act upon an event or be signalled that
 *  there will be no more vents.
 *
 *  This is, in essence, an observer.
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
  extends Reactor[T] with scala.reactive.EventSink {
    def init(dummy: EventSink[T]) {
      registerEventSink(canLeak)
    }

    init(this)

    def react(value: T) = {
      try underlying.react(value)
      catch ignoreNonLethal
    }

    def except(t: Throwable) = {
      try underlying.except(t)
      catch ignoreNonLethal
    }

    def unreact() = {
      try {
        unregisterEventSink(canLeak)
        underlying.unreact()
      } catch ignoreNonLethal
    }
  }

}
