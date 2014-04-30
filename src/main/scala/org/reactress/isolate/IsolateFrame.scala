package org.reactress
package isolate






final class IsolateFrame[@spec(Int, Long, Double) T](
  val eventQueue: EventQueue[T],
  val channel: Channel[T],
  val scheduler: Scheduler
) extends Reactor[T] with EventObserver[T] {

  def apply(event: T): Unit = {
    // TODO
  }

  def react(event: T) {
    // TODO
  }

  def unreact() {
    // TODO
  }

  def init(dummy: IsolateFrame[T]) {
    // subscribe to channel to add events to event queue

    // call the asynchronous foreach on the event queue
  }

  init(this)

}
