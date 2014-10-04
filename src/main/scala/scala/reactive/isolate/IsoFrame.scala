package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.util.control.NonFatal



final class IsoFrame(
  val uid: Long,
  val name: String,
  val isolateSystem: IsoSystem,
  val scheduler: Scheduler,
  val eventQueueFactory: EventQueue.Factory,
  val multiplexer: Multiplexer,
  val newConnector: IsoFrame => Connector[_]
) extends (() => Unit) {
  val state = new IsoFrame.State
  val isolateState = new AtomicReference[IsoFrame.IsoState](IsoFrame.Created)
  val counter = new AtomicLong(0L)
  val errorHandling: PartialFunction[Throwable, Unit] = {
    case NonFatal(t) => isolate.failureEmitter += t
  }
  val schedulerInfo: Scheduler.Info = scheduler.newInfo(this)
  val isolateSourceConnector: Connector[_] = newConnector(this)
  val isolateInternalConnector: Connector[_] = newConnector(this)
  @volatile private[reactive] var isolate: Iso[_] = _

  def isTerminated = isolateState.get == IsoFrame.Terminated

  def isOwned: Boolean = state.READ_STATE == 1

  final def tryOwn(): Boolean = state.CAS_STATE(0, 1)

  final def unOwn(): Unit = state.WRITE_STATE(0)

  def apply(): Unit = wake()

  @tailrec def wake(): Unit = if (isolateState.get != IsoFrame.Terminated) {
    if (!isOwned) {
      if (tryOwn()) scheduler.schedule(this)
      else wake()
    }
  }

  def sourceConnector[T]: Connector[T] = isolateSourceConnector.asInstanceOf[Connector[T]]

  def internalConnector: Connector[InternalEvent] = isolateInternalConnector.asInstanceOf[Connector[InternalEvent]]

  /* running the frame */
  
  def run() {
    try {
      if (isolateState.get != IsoFrame.Terminated) isolateAndRun()
    } finally {
      unOwn()
      if (!multiplexer.areEmpty) {
        if (isolateState.get != IsoFrame.Terminated) wake()
      }
    }
  }

  private def isolateAndRun() {
    if (Iso.selfIso.get != null) {
      throw new IllegalStateException(s"Cannot execute isolate inside of another isolate: ${Iso.selfIso.get}.")
    }
    try {
      Iso.selfIso.set(isolate)
      runInsideIso()
    } catch {
      scheduler.handler
    } finally {
      Iso.selfIso.set(null)
    }
  }

  @tailrec private def checkCreated() {
    import IsoFrame._
    if (isolateState.get == Created) {
      if (isolateState.compareAndSet(Created, Running)) isolate.systemEmitter += IsoStarted
      else checkCreated()
    }
  }

  private def checkEmptyQueue() {
    if (multiplexer.areEmpty) isolate.systemEmitter += IsoEmptyQueue
  }

  @tailrec private def checkTerminated() {
    import IsoFrame._
    if (multiplexer.isTerminated && isolateState.get == Running) {
      if (isolateState.compareAndSet(Running, Terminated)) {
        try isolate.systemEmitter += IsoTerminated
        finally try for (es <- isolate.eventSources) es.close()
        finally isolateSystem.releaseName(name)
      } else checkTerminated()
    }
  }

  private def runInsideIso() {
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


object IsoFrame {

  /** Ownership state of the isolate frame - 0 is not owned, 1 is owned.
   */
  final class State {
    @volatile private[reactive] var state: Int = 0

    def READ_STATE: Int = state

    def WRITE_STATE(v: Int): Unit = util.unsafe.putIntVolatile(this, IsoFrame.STATE_OFFSET, v)

    def CAS_STATE(ov: Int, nv: Int): Boolean = util.unsafe.compareAndSwapInt(this, IsoFrame.STATE_OFFSET, ov, nv)
  }

  val STATE_OFFSET = util.unsafe.objectFieldOffset(classOf[State].getDeclaredField("state"))

  sealed trait IsoState
  case object Created extends IsoState
  case object Running extends IsoState
  case object Terminated extends IsoState

}

