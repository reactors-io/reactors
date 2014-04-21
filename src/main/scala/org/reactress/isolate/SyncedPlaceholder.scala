package org.reactress
package isolate



import scala.annotation.tailrec
import scala.collection._



class SyncedPlaceholder[@spec(Int, Long, Double) T: Arrayable]
  (val executionService: SyncedScheduler, val channels: Reactive[Reactive[T]], val newIsolate: Reactive[T] => Isolate[T], w: AnyRef)
extends Reactor[Reactive[T]] with Runnable {
  @volatile private[isolate] var state: SyncedPlaceholder.State = SyncedPlaceholder.Idle
  @volatile private[isolate] var eventQueue: util.UnrolledRing[T] = _
  @volatile private[isolate] var emitter: Reactive.Emitter[T] = _
  @volatile private[isolate] var monitor: AnyRef = _
  @volatile private[isolate] var work: Runnable = _
  @volatile private[isolate] var worker: AnyRef = _
  @volatile private[isolate] var liveChannels: mutable.Set[Reactive[T]] = _
  @volatile private[isolate] var channelsTerminated: Boolean = _
  @volatile private[isolate] var shouldTerminate: Boolean = _
  @volatile private[isolate] var isolate: Isolate[T] = _
  private var default: T = _

  def init(dummy: T) {
    eventQueue = new util.UnrolledRing[T]
    emitter = new Reactive.Emitter[T]
    monitor = new AnyRef
    isolate = newIsolate(emitter)
    work = executionService.runnableInIsolate(this, isolate)
    worker = w
    liveChannels = mutable.Set()
    channelsTerminated = false
    shouldTerminate = false
    channels.onReaction(this)
  }

  init(default)

  private def checkShouldTerminate() {
    if (channelsTerminated && liveChannels.isEmpty) shouldTerminate = true
    executionService.requestProcessing(this)
  }

  private def unbind(r: Reactive[T]): Unit = monitor.synchronized {
    liveChannels -= r
    checkShouldTerminate()
  }

  def unreact(): Unit = monitor.synchronized {
    channelsTerminated = true
    checkShouldTerminate()
  }

  def react(r: Reactive[T]) = monitor.synchronized {
    liveChannels += r
    r.onReaction(new Reactor[T] {
      def react(event: T) = scheduleEvent(event)
      def unreact() = unbind(r)
    })
  }

  def scheduleEvent(event: T) = monitor.synchronized {
    eventQueue.enqueue(event)
    if (state == SyncedPlaceholder.Idle) {
      executionService.requestProcessing(this)
      state = SyncedPlaceholder.Requested
    }
  }

  def chunk = 50

  def run() = {
    var claimed = false
    try {
      monitor.synchronized {
        if (state != SyncedPlaceholder.Claimed) {
          state = SyncedPlaceholder.Claimed
          claimed = true
        }
      }
      if (claimed) run(chunk)
    } finally {
      monitor.synchronized {
        if (claimed) {
          if (eventQueue.nonEmpty) {
            executionService.requestProcessing(this)
            state = SyncedPlaceholder.Requested
          } else {
            state = SyncedPlaceholder.Idle
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


object SyncedPlaceholder {
  trait State
  case object Idle extends State
  case object Requested extends State
  case object Claimed extends State
}

