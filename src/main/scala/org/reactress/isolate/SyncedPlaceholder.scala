package org.reactress
package isolate



import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection._



class SyncedPlaceholder[@spec(Int, Long, Double) T: Arrayable]
  (val syncedScheduler: SyncedScheduler, val channels: Reactive[Reactive[T]], val newIsolate: Reactive[T] => Isolate[T], w: AnyRef)
extends Reactor[Reactive[T]] with Runnable {
  @volatile private[isolate] var state: SyncedPlaceholder.State = SyncedPlaceholder.Idle
  @volatile private[isolate] var eventQueue: util.UnrolledRing[T] = _
  @volatile private[isolate] var emitter: Reactive.Emitter[T] = _
  @volatile private[isolate] var monitor: AnyRef = _
  @volatile private[isolate] var work: Runnable = _
  @volatile private[isolate] var worker: AnyRef = _
  @volatile private[isolate] var liveChannels: mutable.MultiMap[Reactive[T], Reactive.Subscription] = _
  @volatile private[isolate] var channelsTerminated: Boolean = _
  @volatile private[isolate] var shouldTerminate: Boolean = _
  @volatile private[isolate] var isolate: Isolate[T] = _
  @volatile private[isolate] var isolateState: AtomicReference[Isolate.State] = _
  private var default: T = _

  def init(dummy: T) {
    eventQueue = new util.UnrolledRing[T]
    emitter = new Reactive.Emitter[T]
    monitor = new AnyRef
    isolate = newIsolate(emitter)
    work = syncedScheduler.runnableInIsolate(this, isolate)
    worker = w
    liveChannels = new mutable.HashMap[Reactive[T], mutable.Set[Reactive.Subscription]] with mutable.MultiMap[Reactive[T], Reactive.Subscription]
    channelsTerminated = false
    shouldTerminate = false
    isolateState = new AtomicReference(Isolate.Created)
    channels.onReaction(this)
  }

  init(default)

  @tailrec final def trySetRunning(): Boolean = {
    val s0 = isolateState.get
    if (s0 != Isolate.Created) false
    else {
      if (isolateState.compareAndSet(s0, Isolate.Running)) true
      else trySetRunning()
    }
  }

  private def checkShouldTerminate() {
    if (channelsTerminated && liveChannels.isEmpty) shouldTerminate = true
    syncedScheduler.requestProcessing(this)
  }

  private def unbind(reactive: Reactive[T], reactor: Reactive.Subscription): Unit = monitor.synchronized {
    liveChannels.removeBinding(reactive, reactor)
    checkShouldTerminate()
  }

  def unreact(): Unit = monitor.synchronized {
    channelsTerminated = true
    checkShouldTerminate()
  }

  def react(r: Reactive[T]) = monitor.synchronized {
    @volatile var sub: Reactive.Subscription = null
    sub = r.onReaction(new Reactor[T] {
      def react(event: T) = scheduleEvent(event)
      def unreact() = unbind(r, sub)
    })
    liveChannels.addBinding(r, sub)
  }

  def scheduleEvent(event: T) = monitor.synchronized {
    eventQueue.enqueue(event)
    if (state == SyncedPlaceholder.Idle) {
      syncedScheduler.requestProcessing(this)
      state = SyncedPlaceholder.Requested
    }
  }

  def chunk = 100

  def run() = {
    var claimed = false
    try {
      monitor.synchronized {
        if (state != SyncedPlaceholder.Claimed) {
          state = SyncedPlaceholder.Claimed
          claimed = true
        }
      }
      if (claimed) runClaimedIsolate(chunk)
    } finally {
      var emptyEventQueue = false
      monitor.synchronized {
        if (claimed) {
          if (eventQueue.nonEmpty) {
            syncedScheduler.requestProcessing(this)
            state = SyncedPlaceholder.Requested
          } else {
            state = SyncedPlaceholder.Idle
          }
        }
        emptyEventQueue = eventQueue.isEmpty
      }
      if (claimed && emptyEventQueue) {
        isolate.sysEventsEmitter += Isolate.EmptyEventQueue
      }
      if (claimed && shouldTerminate && emptyEventQueue) {
        isolate.sysEventsEmitter += Isolate.Terminate
      }
    }
  }

  @tailrec private def runClaimedIsolate(n: Int) {
    if (trySetRunning()) isolate.sysEventsEmitter += Isolate.Start

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
      if (n > 0) runClaimedIsolate(n - 1)
    }
  }
}


object SyncedPlaceholder {
  trait State
  case object Idle extends State
  case object Requested extends State
  case object Claimed extends State
}

