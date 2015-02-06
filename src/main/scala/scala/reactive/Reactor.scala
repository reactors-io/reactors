package scala.reactive






/** An object that can act upon an event or be signalled that
 *  there will be no more vents.
 * 
 *  This is, in essence, an observer.
 * 
 *  @tparam T        type of events the observer responds to
 */
trait Reactor[@spec(Int, Long, Double) -T] {
  
  /** Called by a reactive or a signal when an event `value` is produced.
   * 
   *  @param value   the event passed to the observer
   */
  def react(value: T): Unit
  
  /** Called by a reactive or a signal when there will be no further updates.
   */
  def unreact(): Unit
}


object Reactor {

  class EventSink[@spec(Int, Long, Double) T](val underlying: Reactor[T])
  extends Reactor[T] with scala.reactive.EventSink {
    def init(dummy: EventSink[T]) {
      registerEventSink()
    }

    init(this)

    def react(value: T) = underlying.react(value)

    def unreact() = {
      unregisterEventSink()
      underlying.unreact()
    }
  }

}
