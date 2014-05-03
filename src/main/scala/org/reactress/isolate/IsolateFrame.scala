package org.reactress
package isolate



import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.util.control.NonFatal



final class IsolateFrame[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](
  val name: String,
  val isolateSystem: IsolateSystem,
  val eventQueue: EventQueue[Q],
  val systemEmitter: Reactive.Emitter[SysEvent],
  val sourceEmitter: Reactive.Emitter[Q],
  val failureEmitter: Reactive.Emitter[Throwable],
  val scheduler: Scheduler,
  val state: IsolateFrame.State,
  val isolateState: AtomicReference[IsolateFrame.IsolateState]
) extends Reactor[T] {
  @volatile private[reactress] var isolate: ReactIsolate[T, Q] = _
  @volatile private[reactress] var channel: Channel[T] = _
  @volatile private[reactress] var dequeuer: Dequeuer[Q] = _
  @volatile private[reactress] var errorHandling: PartialFunction[Throwable, Unit] = _
  @volatile private[reactress] var terminating = false
  @volatile var schedulerInfo: AnyRef = _

  private def propagate(event: Q) {
    try sourceEmitter += event
    catch errorHandling
    finally {}
  }

  def react(event: T) {
    isolate.later += event
  }

  def isTerminating = terminating

  def isOwned: Boolean = state.READ_STATE == 1

  final def tryOwn(): Boolean = state.CAS_STATE(0, 1)

  final def unOwn(): Unit = state.WRITE_STATE(0)

  def unreact() {
    // channel and all its reactives have been closed
    // so no new messages will be added to the event queue
    terminating = true
    wake()
  }

  @tailrec def wake(): Unit = if (isolateState.get != IsolateFrame.Terminated) {
    if (!isOwned) {
      if (tryOwn()) scheduler.schedule(this)
      else wake()
    }
  }

  def init(dummy: IsolateFrame[T, Q]) {
    // call the asynchronous foreach on the event queue
    dequeuer = eventQueue.foreach(this)(scheduler)

    // send to failure emitter
    errorHandling = {
      case NonFatal(t) =>
        failureEmitter += t
    }
  }

  init(this)

  /* running the frame */

  def run(dummy: Dequeuer[Q]) {
    try {
      isolateAndRun(dequeuer)
    } finally {
      unOwn()
      if (dequeuer.nonEmpty || terminating) {
        wake()
      }
    }
  }

  private def isolateAndRun(dummy: Dequeuer[Q]) {
    if (ReactIsolate.selfIsolate.get != null) {
      throw new IllegalStateException(s"Cannot execute isolate inside of another isolate: ${ReactIsolate.selfIsolate.get}.")
    }
    try {
      ReactIsolate.selfIsolate.set(isolate)
      runInIsolate(dequeuer)
    } catch {
      scheduler.handler
    } finally {
      ReactIsolate.selfIsolate.set(null)
    }
  }

  @tailrec private def checkCreated() {
    import IsolateFrame._
    if (isolateState.get == Created) {
      if (isolateState.compareAndSet(Created, Running)) systemEmitter += IsolateStarted
      else checkCreated()
    }
  }

  private def checkEmptyQueue() {
    if (dequeuer.isEmpty) systemEmitter += IsolateEmptyQueue
  }

  @tailrec private def checkTerminating() {
    import IsolateFrame._
    if (terminating && dequeuer.isEmpty && isolateState.get == Running) {
      if (isolateState.compareAndSet(Running, Terminated)) systemEmitter += IsolateTerminated
      else checkTerminating()
    }
  }

  private def runInIsolate(dummy: Dequeuer[Q]) {
    try {
      checkCreated()
      var budget = 50
      while (dequeuer.nonEmpty && budget > 0) {
        val event = dequeuer.dequeue()
        propagate(event)
        budget -= 1
      }
    } finally {
      try checkEmptyQueue()
      finally checkTerminating()
    }
  }

}


object IsolateFrame {

  final class State {
    @volatile private[reactress] var state: Int = 0

    def READ_STATE: Int = state

    def WRITE_STATE(v: Int): Unit = util.unsafe.putIntVolatile(this, IsolateFrame.STATE_OFFSET, v)

    def CAS_STATE(ov: Int, nv: Int): Boolean = util.unsafe.compareAndSwapInt(this, IsolateFrame.STATE_OFFSET, ov, nv)
  }

  val STATE_OFFSET = util.unsafe.objectFieldOffset(classOf[State].getDeclaredField("state"))

  sealed trait IsolateState
  case object Created extends IsolateState
  case object Running extends IsolateState
  case object Terminated extends IsolateState

}

