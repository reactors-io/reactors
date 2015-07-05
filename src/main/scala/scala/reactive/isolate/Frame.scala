package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.collection._
import scala.reactive.core.UnrolledRing
import scala.reactive.util.Monitor



final class Frame(
  val uid: Long,
  val scheduler: Scheduler2,
  val isolateSystem: IsoSystem
) extends Identifiable {
  private[reactive] val monitor = new Monitor
  private[reactive] val connectors = new UniqueStore[Conn[_]]("channel", monitor)
  private[reactive] var executing = false
  private[reactive] var lifecycleState: Frame.LifecycleState = Frame.Fresh
  private[reactive] val pendingQueues = new UnrolledRing[Conn[_]]

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
    val uname = connectors.tryStore(name, conn)

    // 3. return connector
    conn
  }

  def scheduleForExecution() {
    monitor.synchronized {
      if (!executing) {
        executing = true
        scheduler.schedule(this)
      }
    }
  }

  def enqueueEvent[@spec(Int, Long, Double) T](uid: Long, queue: EventQ[T], x: T) {
    // 1. add the event to the event queue
    val size = queue.enqueue(x)

    // 2. check if the frame should be scheduled for execution
    if (size == 1) monitor.synchronized {
      // 3. add the queue to pending queues
      val conn = connectors.forId(uid)
      pendingQueues.enqueue(conn)

      // 4. schedule the frame for later execution
      scheduleForExecution()
    }
  }

  def executeBatch() {
    // this method can only be called if the frame is in the "executing" state
    assert(executing)

    try {
      // if the frame was never run, initialize the frame
      var runCtor = false
      monitor.synchronized {
        if (lifecycleState == Frame.Fresh) {
          lifecycleState = Frame.Running
          runCtor = true
        }
      }

      if (runCtor) {
        // TODO: instantiate the isolate
      }
    } finally {
      // set the execution state to false if no more events
      // or re-schedule
      monitor.synchronized {
        if (hasPendingEvents) {
          scheduler.schedule(this)
        } else {
          executing = false
        }
      }
    }
  }

  def hasTerminated: Boolean = monitor.synchronized {
    lifecycleState == Frame.Terminated
  }

  def numPendingEvents: Int = {
    ???
  }

  def hasPendingEvents: Boolean = monitor.synchronized {
    pendingQueues.nonEmpty
  }

  def dequeueEvent() {
    ???
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
