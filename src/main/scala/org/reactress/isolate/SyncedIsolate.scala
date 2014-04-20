package org.reactress
package isolate



import annotation.tailrec



class SyncedIsolate[@spec(Int, Long, Double) T: Arrayable](val executionService: SyncedScheduler)
extends Isolate[T] with Runnable {
  @volatile private[isolate] var state: SyncedIsolate.State = SyncedIsolate.Idle
  @volatile private[isolate] var eventQueue: util.UnrolledRing[T] = _
  @volatile private[isolate] var emitter: Reactive.Emitter[T] = _
  @volatile private[isolate] var monitor: AnyRef = _
  @volatile private[isolate] var work: Runnable = _
  @volatile private[isolate] var info: AnyRef = _
  private var default: T = _

  def init(dummy: T) {
    eventQueue = new util.UnrolledRing[T]
    emitter = new Reactive.Emitter[T]
    monitor = new AnyRef
    work = executionService.runnableInIsolate(this, this)
  }

  init(default)

  def bind(r: Reactive[T]): Reactive.Subscription = r onValue scheduleEvent

  def scheduleEvent(event: T) = monitor.synchronized {
    eventQueue.enqueue(event)
    if (state == SyncedIsolate.Idle) {
      executionService.requestProcessing(this)
      state = SyncedIsolate.Requested
    }
  }

  def run() = {
    var claimed = false
    try {
      monitor.synchronized {
        if (state != SyncedIsolate.Claimed) {
          state = SyncedIsolate.Claimed
          claimed = true
        }
      }
      run(50)
    } finally {
      monitor.synchronized {
        if (claimed) {
          if (eventQueue.nonEmpty) {
            executionService.requestProcessing(this)
            state = SyncedIsolate.Requested
          } else {
            state = SyncedIsolate.Idle
          }
        }
      }
    }
  }

  @tailrec private def run(n: Int) {
    var event = default
    var shouldEmit = false
    monitor.synchronized {
      if (eventQueue.nonEmpty) {
        event = eventQueue.dequeue()
        shouldEmit = true
      }
    }
    if (shouldEmit) {
      emitter += event
      if (n > 0) run(n - 1)
    }
  }
}


object SyncedIsolate {
  trait State
  case object Idle extends State
  case object Requested extends State
  case object Claimed extends State
}

