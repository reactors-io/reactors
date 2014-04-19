package org.reactress
package isolate







class SyncedIsolate[@spec(Int, Long, Double) T: Arrayable](val executionService: SyncedScheduler)
extends Isolate[T] {
  private[isolate] val eventQueue = new util.UnrolledRing[T]
  private[isolate] val emitter = new Reactive.Emitter[T]
  private[isolate] var state: SyncedIsolate.State = SyncedIsolate.Idle
  private[isolate] val monitor = new AnyRef

  def bind(r: Reactive[T]): Reactive.Subscription = r onValue scheduleEvent

  def scheduleEvent(event: T) = monitor.synchronized {
    eventQueue.enqueue(event)
    if (state == SyncedIsolate.Idle) {
      executionService.requestProcessing(this)
      state = SyncedIsolate.Requested
    }
  }
}


object SyncedIsolate {
  trait State
  case object Idle extends State
  case object Requested extends State
  case object Claimed extends State
}

