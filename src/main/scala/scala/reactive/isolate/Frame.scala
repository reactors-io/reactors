package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.collection._
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

  def enqueueEvent[@spec(Int, Long, Double) Q](uid: Long, queue: EventQ[Q], x: Q) {
    monitor.synchronized {
      // 1. add the event to the event queue
      queue.enqueue(x)

      // 2. schedule the execute frame for execution if it is not executing already
      if (!executing) {
        executing = true
        scheduler.schedule(frame)
      }
    }
  }

  def executeBatch() {
    try {
      // if the frame was never run, initialize the frame
      var runCtor = false
      monitor.synchronized {
        if (lifecycleState == Frame.Fresh) {
          lifecycleState = Frame.Created
          runCtor = true
        }
      }

      if (runCtor) {
        ???
      }
    } finally {
      // set the execution state to false if no more events
      // or re-schedule
      monitor.synchronized {
        if (hasPendingEvents) {
          scheduler.schedule(frame)
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

  def hasPendingEvents: Boolean = {
    ???
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
