package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection._
import scala.reactive.core.UnrolledRing
import scala.reactive.util.Monitor



final class Frame(
  val uid: Long,
  val proto: Proto[Iso[_]],
  val scheduler: Scheduler2,
  val isolateSystem: IsoSystem
) extends Identifiable {
  private[reactive] val monitor = new Monitor
  private[reactive] val connectors = new UniqueStore[Conn[_]]("channel", monitor)
  private[reactive] var nonDaemonCount = 0
  private[reactive] var executing = false
  private[reactive] var lifecycleState: Frame.LifecycleState = Frame.Fresh
  private[reactive] val pendingQueues = new UnrolledRing[Conn[_]]

  @volatile var iso: Iso[_] = _
  @volatile var name: String = _
  @volatile var defaultConnector: Conn[_] = _
  @volatile var systemConnector: Conn[_] = _
  @volatile var schedulerState: Scheduler2.State = _

  def openConnector[@spec(Int, Long, Double) Q: Arrayable](
    name: String,
    factory: EventQ.Factory,
    isDaemon: Boolean
  ): Conn[Q] = {
    // 1. prepare and ensure a unique id
    val uid = connectors.reserveId()
    val queue = factory.newInstance[Q]
    val chan = new Chan.Local[Q](uid, queue, this)
    val events = new Events.Emitter[Q]
    val conn = new Conn(chan, queue, events, this, isDaemon)

    // 2. acquire a unique name or break
    val uname = monitor.synchronized {
      val u = connectors.tryStore(name, conn)
      if (!isDaemon) nonDaemonCount += 1
      u
    }

    // 3. return connector
    conn
  }

  /** Atomically schedules the frame for execution, unless already scheduled.
   */
  def scheduleForExecution() {
    var mustSchedule = false
    monitor.synchronized {
      if (!executing) {
        executing = true
        mustSchedule = true
      }
    }
    if (mustSchedule) scheduler.schedule(this)
  }

  def enqueueEvent[@spec(Int, Long, Double) T](uid: Long, queue: EventQ[T], x: T) {
    // 1. add the event to the event queue
    val size = queue.enqueue(x)

    // 2. check if the frame should be scheduled for execution
    var mustSchedule = false
    if (size == 1) monitor.synchronized {
      // 3. add the queue to pending queues
      val conn = connectors.forId(uid)
      pendingQueues.enqueue(conn)

      // 4. schedule the frame for later execution
      if (!executing) {
        executing = true
        mustSchedule = true
      }
    }
    if (mustSchedule) scheduler.schedule(this)
  }

  def executeBatch() {
    // 1. check the state
    // this method can only be called if the frame is in the "executing" state
    assert(executing)

    // this method cannot be executed inside another isolate
    if (Iso.selfIso.get != null) {
      throw new IllegalStateException(
        s"Cannot execute isolate inside of another isolate: ${Iso.selfIso.get}.")
    }

    try {
      isolateAndProcessBatch()
    } finally {
      // set the execution state to false if no more events, or otherwise re-schedule
      var mustSchedule = false
      monitor.synchronized {
        if (pendingQueues.nonEmpty) {
          mustSchedule = true
        } else {
          executing = false
        }
      }
      if (mustSchedule) scheduler.schedule(this)
    }
  }

  private def isolateAndProcessBatch() {
    try {
      Iso.selfIso.set(iso)
      Iso.selfFrame.set(this)
      processBatch()
    } catch {
      scheduler.handler
    } finally {
      Iso.selfIso.set(null)
      Iso.selfFrame.set(null)
    }
  }

  private def checkFresh() {
    var runCtor = false
    monitor.synchronized {
      if (lifecycleState == Frame.Fresh) {
        lifecycleState = Frame.Running
        runCtor = true
      }
    }
    if (runCtor) {
      iso = proto.create()
      iso.sysEmitter.react(IsoStarted)
    }
  }

  private def nextPending(): Conn[_] = monitor.synchronized {
    if (pendingQueues.nonEmpty) pendingQueues.dequeue()
    else null
  }

  private def processEvents() {
    schedulerState.onBatchStart(this)

    // precondition: there is at least one pending event
    @tailrec def loop(c: Conn[_]): Unit = {
      val remaining = c.dequeue()
      schedulerState.onBatchEvent(this)
      if (schedulerState.canConsume) {
        // need to consume some more
        if (remaining > 0) loop(c)
        else {
          val nc = nextPending()
          if (nc != null) loop(nc)
        }
      } else {
        // done consuming -- see if the connector needs to be enqueued
        if (remaining > 0) monitor.synchronized {
          pendingQueues.enqueue(c)
        }
      }
    }
    val nc = nextPending()
    if (nc != null) loop(nc)

    schedulerState.onBatchStop(this)
  }

  private def checkTerminated() {
    monitor.synchronized {
      pendingQueues.isEmpty && nonDaemonCount == 0
    }
  }

  private def processBatch() {
    checkFresh()
    processEvents()
    checkTerminated()
  }

  def hasTerminated: Boolean = monitor.synchronized {
    lifecycleState == Frame.Terminated
  }

  def hasPendingEvents: Boolean = monitor.synchronized {
    pendingQueues.nonEmpty
  }

  def estimateTotalPendingEvents: Int = monitor.synchronized {
    var count = 0
    for (c <- pendingQueues) count += c.queue.size
    count
  }

  def isConnectorSealed(uid: Long): Boolean = {
    ???
  }

  def sealConnector(uid: Long): Boolean = {
    ???
  }

}


object Frame {

  sealed trait LifecycleState

  case object Fresh extends LifecycleState

  case object Running extends LifecycleState

  case object Terminated extends LifecycleState

}
