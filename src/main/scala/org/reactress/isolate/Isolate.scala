package org.reactress
package isolate






trait Isolate[@spec(Int, Long, Double) T] {
  def bind(r: Reactive[T]): Reactive.Subscription
}


object Isolate {

  class Synced[@spec(Int, Long, Double) T: Arrayable](val executionService: Reactor.SyncedExecutionService) extends Isolate[T] {
    private[isolate] val eventQueue = new util.UnrolledRing[T]
    private[isolate] val emitter = new Reactive.Emitter[T]
    private[isolate] var state: Synced.State = Synced.Idle
    private[isolate] val monitor = new AnyRef

    def bind(r: Reactive[T]): Reactive.Subscription = r onValue scheduleEvent

    def scheduleEvent(event: T) = monitor.synchronized {
      eventQueue.enqueue(event)
      if (state == Synced.Idle) {
        executionService.requestProcessing(this)
        state = Synced.Requested
      }
    }
  }

  object Synced {
    trait State
    case object Idle extends State
    case object Requested extends State
    case object Claimed extends State
  }

}
