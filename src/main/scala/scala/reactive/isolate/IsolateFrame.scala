package scala.reactive
package isolate



import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.util.control.NonFatal



final class IsolateFrame(
  val name: String,
  val isolateSystem: IsolateSystem,
  val scheduler: Scheduler,
  val eventQueueFactory: EventQueue.Factory,
  val multiplexer: Multiplexer,
  val newSourceConnector: IsolateFrame => Connector[_]
) extends (() => Unit) {
  val state = new IsolateFrame.State
  val isolateState = new AtomicReference[IsolateFrame.IsolateState](IsolateFrame.Created)
  val errorHandling: PartialFunction[Throwable, Unit] = {
    case NonFatal(t) => isolate.failureEmitter += t
  }
  val schedulerInfo: Scheduler.Info = scheduler.newInfo(this)
  val isolateSourceConnector: Connector[_] = newSourceConnector(this)
  @volatile private[reactive] var isolate: Isolate[_] = _

  def isTerminated = isolateState.get == IsolateFrame.Terminated

  def isOwned: Boolean = state.READ_STATE == 1

  final def tryOwn(): Boolean = state.CAS_STATE(0, 1)

  final def unOwn(): Unit = state.WRITE_STATE(0)

  def apply(): Unit = wake()

  @tailrec def wake(): Unit = if (isolateState.get != IsolateFrame.Terminated) {
    if (!isOwned) {
      if (tryOwn()) scheduler.schedule(this)
      else wake()
    }
  }

  def sourceConnector[T]: Connector[T] = isolateSourceConnector.asInstanceOf[Connector[T]]

  /* running the frame */
  
  def run() {
    try {
      if (isolateState.get != IsolateFrame.Terminated) isolateAndRun()
    } finally {
      unOwn()
      if (!multiplexer.areEmpty) {
        if (isolateState.get != IsolateFrame.Terminated) wake()
      }
    }
  }

  private def isolateAndRun() {
    if (Isolate.selfIsolate.get != null) {
      throw new IllegalStateException(s"Cannot execute isolate inside of another isolate: ${Isolate.selfIsolate.get}.")
    }
    try {
      Isolate.selfIsolate.set(isolate)
      runInsideIsolate()
    } catch {
      scheduler.handler
    } finally {
      Isolate.selfIsolate.set(null)
    }
  }

  @tailrec private def checkCreated() {
    import IsolateFrame._
    if (isolateState.get == Created) {
      if (isolateState.compareAndSet(Created, Running)) isolate.systemEmitter += IsolateStarted
      else checkCreated()
    }
  }

  private def checkEmptyQueue() {
    if (multiplexer.areEmpty) isolate.systemEmitter += IsolateEmptyQueue
  }

  @tailrec private def checkTerminated() {
    import IsolateFrame._
    if (multiplexer.isTerminated && isolateState.get == Running) {
      if (isolateState.compareAndSet(Running, Terminated)) {
        try isolate.systemEmitter += IsolateTerminated
        finally for (es <- isolate.eventSources) es.close()
      } else checkTerminated()
    }
  }

  private def runInsideIsolate() {
    try {
      checkCreated()
      schedulerInfo.onBatchStart(this)
      while (!multiplexer.areEmpty && schedulerInfo.canSchedule) {
        schedulerInfo.dequeueEvent(this)
        schedulerInfo.onBatchEvent(this)
      }
    } finally {
      schedulerInfo.onBatchStop(this)
      try checkEmptyQueue()
      finally checkTerminated()
    }
  }

}


object IsolateFrame {

  /** Ownership state of the isolate frame - 0 is not owned, 1 is owned.
   */
  final class State {
    @volatile private[reactive] var state: Int = 0

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

