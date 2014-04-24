package org.reactress
package isolate



import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection._



class SyncedPlaceholder[@spec(Int, Long, Double) T: Arrayable]
  (val syncedScheduler: SyncedScheduler, val newIsolate: () => Isolate[T], nullOrWorker: AnyRef)
extends Reactor[T] with Runnable {
  @volatile private[isolate] var state: SyncedPlaceholder.State = SyncedPlaceholder.Idle
  @volatile private[isolate] var monitor: AnyRef = _
  @volatile private[isolate] var work: Runnable = _
  @volatile private[isolate] var worker: AnyRef = _
  @volatile private[isolate] var shouldTerminate: Boolean = _
  @volatile private[isolate] var isolate: Isolate[T] = _
  @volatile private[isolate] var isolateState: AtomicReference[Isolate.State] = _
  @volatile private[isolate] var channel: Channel[T] = _
  private var default: T = _

  def init(dummy: T) {
    monitor = new AnyRef
    channel = new Channel.Synced(this, monitor)
    Isolate.argEventQueue.withValue(new EventQueue.SyncedUnrolledRing(monitor)) {
      Isolate.argChannel.withValue(channel) {
        val oldiso = Isolate.selfIsolate.get
        try {
          isolate = newIsolate()
        } finally {
          Isolate.selfIsolate.set(oldiso)
        }
      }
    }
    work = syncedScheduler.runnableInIsolate(this, isolate)
    worker = nullOrWorker
    shouldTerminate = false
    isolateState = new AtomicReference(Isolate.Created)
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

  def unreact(): Unit = monitor.synchronized {
    shouldTerminate = true
  }

  def react(event: T) = monitor.synchronized {
    scheduleEvent(event)
  }

  def scheduleEvent(event: T) = monitor.synchronized {
    isolate.eventQueue += event
    if (state == SyncedPlaceholder.Idle) {
      syncedScheduler.requestProcessing(this)
      state = SyncedPlaceholder.Requested
    }
  }

  def chunk = 100

  def run() = {
    if (trySetRunning()) isolate.sysEventsEmitter += Isolate.Start

    var empty0 = false
    var empty1 = false
    var claimed = false
    try {
      monitor.synchronized {
        if (state != SyncedPlaceholder.Claimed) {
          state = SyncedPlaceholder.Claimed
          claimed = true
          empty0 = isolate.eventQueue.isEmpty
        }
      }
      if (claimed) runClaimedIsolate(chunk)
    } finally {
      monitor.synchronized {
        if (claimed) {
          empty1 = isolate.eventQueue.isEmpty
          if (!empty1) {
            syncedScheduler.requestProcessing(this)
            state = SyncedPlaceholder.Requested
          } else {
            state = SyncedPlaceholder.Idle
          }
        }
      }
      if (claimed && !empty0 && empty1) {
        isolate.sysEventsEmitter += Isolate.EmptyQueue
      }
      if (claimed && shouldTerminate && !empty0 && empty1) {
        isolate.sysEventsEmitter += Isolate.Terminate
      }
    }
  }

  @tailrec private def runClaimedIsolate(n: Int) {
    try isolate.propagate()
    catch {
      case t: Throwable => isolate.sysEventsEmitter += Isolate.Failed(t)
    }

    if (n > 0 && isolate.eventQueue.nonEmpty) runClaimedIsolate(n - 1)
  }
}


object SyncedPlaceholder {
  trait State
  case object Idle extends State
  case object Requested extends State
  case object Claimed extends State
}

