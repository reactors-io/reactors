package org.reactress
package isolate






final class IsolateFrame[@spec(Int, Long, Double) T](
  val eventQueue: EventQueue[T],
  val channel: Channel[T],
  val scheduler: Scheduler,
  val runnable: Runnable,
  val scheduleState: AnyRef
) extends Reactor[T] {

  def react(event: T) {

  }

  def unreact() {
    
  }

}
