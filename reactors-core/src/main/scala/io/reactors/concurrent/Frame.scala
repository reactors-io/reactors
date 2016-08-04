package io.reactors
package concurrent



import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection._
import scala.util.Random
import io.reactors.common.UnrolledRing
import io.reactors.common.Monitor



/** Placeholder for the reactor's state.
 */
final class Frame(
  val uid: Long,
  val proto: Proto[Reactor[_]],
  val scheduler: Scheduler,
  val reactorSystem: ReactorSystem
) extends Identifiable {
  private[reactors] val monitor = new Monitor
  private[reactors] val connectors = new UniqueStore[Connector[_]]("channel", monitor)
  private[reactors] var nonDaemonCount = 0
  private[reactors] var active = false
  private[reactors] var lifecycleState: Frame.LifecycleState = Frame.Fresh
  private[reactors] val pendingQueues = new UnrolledRing[Connector[_]]
  private[reactors] val sysEmitter = new Events.Emitter[SysEvent]
  private[reactors] var spindown = reactorSystem.bundle.schedulerConfig.spindownInitial
  private[reactors] val schedulerConfig = reactorSystem.bundle.schedulerConfig
  private[reactors] var totalBatches = 0L
  private[reactors] var totalSpindownScore = 0L
  private[reactors] var random = new Random

  @volatile var reactor: Reactor[_] = _
  @volatile var name: String = _
  @volatile var url: ReactorUrl = _
  @volatile var defaultConnector: Connector[_] = _
  @volatile var internalConnector: Connector[_] = _
  @volatile var schedulerState: Scheduler.State = _
  @volatile private var spinsLeft = 0

  def openConnector[@spec(Int, Long, Double) Q: Arrayable](
    name: String,
    factory: EventQueue.Factory,
    isDaemon: Boolean
  ): Connector[Q] = {
    // 1. Prepare and ensure a unique id for the channel.
    val uid = connectors.reserveId()
    val queue = factory.newInstance[Q]
    val chanUrl = ChannelUrl(url, name)
    val localChan = new Channel.Local[Q](uid, this)
    val chan = new Channel.Shared(chanUrl, localChan)
    val conn = new Connector(chan, queue, this, isDaemon)
    localChan.connector = conn

    // 2. Acquire a unique name or break if not unique.
    val uname = monitor.synchronized {
      val u = connectors.tryStore(name, conn)
      if (!isDaemon) nonDaemonCount += 1
      u
    }

    // 3. Return connector.
    conn
  }

  /** Atomically schedules the frame for execution, unless already scheduled.
   */
  def activate() {
    var mustSchedule = false
    monitor.synchronized {
      if (!active) {
        active = true
        mustSchedule = true
      }
    }
    if (mustSchedule) scheduler.schedule(this)
  }

  def enqueueEvent[@spec(Int, Long, Double) T](conn: Connector[T], x: T) {
    // 1. Add the event to the event queue.
    val size = conn.queue.enqueue(x)

    // 2. Check if the frame should be scheduled for execution.
    var mustSchedule = false
    if (size == 1) monitor.synchronized {
      // 3. Add the queue to pending queues.
      pendingQueues.enqueue(conn)

      // 4. Schedule the frame for later execution.
      if (!active) {
        active = true
        mustSchedule = true
      }
    }
    if (mustSchedule) scheduler.schedule(this)
  }

  def executeBatch() {
    // 1. Check the state.
    // This method can only be called if the frame is in the "active" state.
    assert(active)

    // This method cannot be executed inside another reactor.
    if (Reactor.currentReactor != null) {
      throw new IllegalStateException(
        s"Cannot execute reactor inside another reactor: ${Reactor.currentReactor}.")
    }

    // Process a batch of events.
    try {
      isolateAndProcessBatch()
    } finally {
      // Set the execution state to false if no more events, or otherwise re-schedule.
      var mustSchedule = false
      monitor.synchronized {
        if (pendingQueues.nonEmpty && !hasTerminated) {
          mustSchedule = true
        } else {
          active = false
        }
      }
      if (mustSchedule) scheduler.schedule(this)
    }

    // Piggyback the worker thread to do some useful work.
    scheduler.unscheduleAndRun(reactorSystem)
  }

  private def isolateAndProcessBatch() {
    try {
      Reactor.currentFrame = this
      processBatch()
    } catch {
      case t: Throwable =>
        try {
          if (!hasTerminated) {
            if (reactor != null) sysEmitter.react(ReactorDied(t))
          }
        } finally {
          checkTerminated(true)
        }
        throw t
    } finally {
      Reactor.currentFrame = null
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
      reactor = proto.create()
      reactorSystem.debugApi.reactorStarted(reactor)
      sysEmitter.react(ReactorStarted)
    }
  }

  private def popNextPending(): Connector[_] = monitor.synchronized {
    if (pendingQueues.nonEmpty) pendingQueues.dequeue()
    else null
  }

  private def processEvents() {
    schedulerState.onBatchStart(this)

    // Precondition: there is at least one pending event.
    // Return value:
    // - `false` iff stopped by preemption
    // - `true` iff stopped because there are no events
    @tailrec def drain(c: Connector[_]): Boolean = {
      val remaining = c.dequeue()
      if (schedulerState.onBatchEvent(this)) {
        // Need to consume some more.
        if (remaining > 0 && !c.sharedChannel.asLocal.isSealed) {
          drain(c)
        } else {
          val nc = popNextPending()
          if (nc != null) drain(nc)
          else true
        }
      } else {
        // Done consuming -- see if the connector needs to be enqueued.
        if (remaining > 0 && !c.sharedChannel.asLocal.isSealed) monitor.synchronized {
          pendingQueues.enqueue(c)
        }
        false
      }
    }

    var nc = popNextPending()
    var spindownScore = 0
    while (nc != null) {
      if (drain(nc)) {
        // Wait a bit for additional events, since preemption is expensive.
        nc = null
        spinsLeft = spindown
        while (spinsLeft > 0) {
          spinsLeft -= 1
          if (spinsLeft % 10 == 0) {
            nc = popNextPending()
            if (nc != null) spinsLeft = 0
          }
        }
        if (nc != null) spindownScore += 1
      } else {
        nc = null
      }
    }
    totalBatches += 1
    totalSpindownScore += spindownScore
    val spindownMutationRate = schedulerConfig.spindownMutationRate
    if (random.nextDouble() < spindownMutationRate || spindownScore >= 1) {
      var spindownCoefficient = 1.0 * totalSpindownScore / totalBatches
      val threshold = schedulerConfig.spindownTestThreshold
      val iters = schedulerConfig.spindownTestIterations
      if (totalBatches >= threshold) {
        spindownCoefficient += math.max(0.0, 1.0 - (totalBatches - threshold) / iters)
      }
      spindownCoefficient = math.min(1.0, spindownCoefficient)
      spindown = (schedulerConfig.spindownMax * spindownCoefficient).toInt
    }
    spindown -= (spindown / schedulerConfig.spindownCooldownRate + 1)
    spindown = math.max(schedulerConfig.spindownMin, spindown)
    // if (random.nextDouble() < 0.0001)
    //   println(spindown, 1.0 * totalSpindownScore / totalBatches)
  }

  private def checkTerminated(forcedTermination: Boolean) {
    var emitTerminated = false
    monitor.synchronized {
      if (lifecycleState == Frame.Running) {
        if (forcedTermination || (pendingQueues.isEmpty && nonDaemonCount == 0)) {
          lifecycleState = Frame.Terminated
          emitTerminated = true
        }
      }
    }
    if (emitTerminated) {
      try {
        reactorSystem.debugApi.reactorTerminated(reactor)
        if (reactor != null) sysEmitter.react(ReactorTerminated)
      } finally {
        reactorSystem.frames.tryRelease(name)
      }
    }
  }

  private def processBatch() {
    checkFresh()
    try {
      sysEmitter.react(ReactorScheduled)
      processEvents()
      sysEmitter.react(ReactorPreempted)
    } finally {
      checkTerminated(false)
    }
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

  def sealConnector(uid: Long): Boolean = {
    val sealedConn = monitor.synchronized {
      val conn = connectors.forId(uid)
      if (conn == null) null
      else {
        conn.sharedChannel.asLocal.isOpen = false
        decrementConnectorCount(conn)
        assert(connectors.tryReleaseById(uid))
        conn
      }
    }
    if (sealedConn != null) {
      assert(Reactor.selfAsOrNull != null)
      sealedConn.queue.unreact()
    }
    sealedConn != null
  }

  def decrementConnectorCount(conn: Connector[_]) = monitor.synchronized {
    if (!conn.isDaemon) nonDaemonCount -= 1
  }

}


object Frame {
  sealed trait LifecycleState

  case object Fresh extends LifecycleState

  case object Running extends LifecycleState

  case object Terminated extends LifecycleState
}
