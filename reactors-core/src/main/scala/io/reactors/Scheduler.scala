package io.reactors



import java.util.concurrent._
import java.util.concurrent.atomic.AtomicReference
import java.util.TimerTask
import scala.collection._
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec
import io.reactors.common.freshId
import io.reactors.concurrent._



/** An object that schedules reactors for execution.
 *
 *  After a reactor is instantiated, its reactor frame is assigned a scheduler by the
 *  reactor system.
 *  A reactor that is assigned a specific scheduler will always be executed on that
 *  same scheduler.
 *
 *  After creating a reactor, every reactor system will first call the `initSchedule`
 *  method on the reactor frame.
 *  Then, the reactor system will call the `schedule` method every time there are events
 *  ready for the reactor.
 *
 *  '''Note:'''
 *  Clients never invoke `Scheduler` operations directly,
 *  but can implement their own scheduler if necessary.
 *
 *  @see [[org.reactors.ReactorSystem]]
 */
trait Scheduler {

  /** Notifies a reactor frame that it should be executed.
   *  Clients never call this method directly.
   *
   *  This method uses the reactor frame to flush messages from its event queue
   *  and propagate events through the reactor.
   *
   *  @param frame      the reactor frame to schedule
   */
  def schedule(frame: Frame): Unit

  /** Tells the scheduler to start listening to schedule requests for the reactor frame.
   *  Clients never call this method directly.
   *
   *  By default, assigns the default scheduler state to the `schedulerState` field in
   *  the reactor frame.
   *
   *  @param frame      the reactor frame to start scheduling
   */
  def initSchedule(frame: Frame): Unit = {
    frame.schedulerState = newState(frame)
  }

  /** Called immediately before a reactor frame begins an execution batch.
   */
  def preschedule(system: ReactorSystem): Unit = {}

  /** Called immediately after a reactor frame completes an execution batch.
   *
   *  Optionally unschedules and runs some number of frames previously scheduled.
   *
   *  This method by default does nothing, but may be overridden for performance
   *  purposes.
   */
  def unschedule(system: ReactorSystem, t: Throwable): Unit = {}

  /** The handler for the fatal errors that are not sent to
   *  the `failures` stream of the reactor.
   *
   *  '''Note:'''
   *  If the `failures` event stream throws
   *  while handling any throwables passed to it,
   *  then those throwables are passed to this error handler.
   *  This means that the `handler` can also receive non-fatal errors.
   *
   *  @see [[scala.util.control.NonFatal]]
   */
  def handler: Scheduler.Handler

  /** Creates an `State` object for the reactor frame.
   *
   *  @param frame       the reactor frame
   *  @return            creates a fresh scheduler info object
   */
  protected def newState(frame: Frame): Scheduler.State = new Scheduler.State.Default {}

}


/** Companion object for creating standard reactor schedulers.
 */
object Scheduler {

  /** Superclass for the information objects that a scheduler attaches to a reactor
   *  frame.
   */
  trait State {
    /** Called when a batch of events are about to be handled.
     *  
     *  @param frame    the reactor frame
     */
    def onBatchStart(frame: Frame): Unit = {
    }

    /** Called just before an event gets scheduled.
     *  
     *  @param frame    the reactor frame
     *  @return         `true` if scheduler can consume more events, `false` otherwise
     */
    def onBatchEvent(frame: Frame): Boolean
  }

  object State {
    /** The default info object implementation.
     */
    trait Default extends State {
      @volatile var allowedBudget: Long = _

      override def onBatchStart(frame: Frame): Unit = {
        allowedBudget = frame.reactorSystem.bundle.schedulerConfig.defaultBudget
      }

      def onBatchEvent(frame: Frame): Boolean = {
        allowedBudget -= 1
        allowedBudget > 0
      }
    }
  }

  type Handler = PartialFunction[Throwable, Unit]

  /** The default handler prints the exception to the standard error stream.
   */
  val defaultHandler: Handler = {
    case t: Throwable => t.printStackTrace()
  }

  /** Silent handler ignores exceptions.
   */
  val silentHandler: Handler = {
    case t: Throwable => // do nothing
  }

  /** Scheduler that shares the global Scala execution context.
   */
  lazy val globalExecutionContext: Scheduler =
    new Executed(ExecutionContext.Implicits.global)

  private[reactors] class ReactorForkJoinWorkerThread(pool: ReactorForkJoinPool)
  extends ForkJoinWorkerThread(pool) with Reactor.ReactorLocalThread {
    import ReactorForkJoinWorkerThread._
    val miniQueue = new AtomicReference[Frame](null)
    private var state: AnyRef = ASLEEP

    setName(s"reactors-io-scheduler-${getName}")

    override def onStart() {
      super.onStart()
      // TODO: Add to worker list.
    }

    override def onTermination(t: Throwable) {
      reactorPool.workers.remove(getId)
      super.onTermination(t)
    }

    @tailrec
    final def execute(frame: Frame) {
      val state = miniQueue.get
      if (state eq null) {
        if (!miniQueue.compareAndSet(state, frame)) execute(frame)
      } else {
        if (!miniQueue.compareAndSet(state, null)) execute(frame)
        else {
          val r = state.schedulerState.asInstanceOf[Runnable]
          ForkJoinTask.adapt(r).fork()
          execute(frame)
        }
      }
    }

    private def pollPool(fj: ReactorForkJoinPool): Boolean = {
      val task = fj.poll()
      if (task != null) {
        task.invoke()
        true
      } else false
    }

    @tailrec
    private def popMiniQueue(): Frame = {
      val state = miniQueue.get
      if (state != null) {
        if (!miniQueue.compareAndSet(state, null)) popMiniQueue()
        else state
      } else null
    }

    private def executeLater(frame: Frame) {
      if (frame != null) {
        val r = frame.schedulerState.asInstanceOf[Runnable]
        ForkJoinTask.adapt(r).fork()
      }
    }

    private def executeNow(frame: Frame): Boolean = {
      if (frame != null) {
        frame.executeBatch()
        true
      } else false
    }

    def preschedule() {
      if (state != UNSCHEDULING) state = AWAKE
    }

    def unschedule(system: ReactorSystem, t: Throwable) {
      if (state == UNSCHEDULING) return
      if (t != null) {
        state = ASLEEP
        executeLater(popMiniQueue())
        return
      } else {
        state = UNSCHEDULING
      }

      try {
        getPool match {
          case fj: ReactorForkJoinPool =>
            var loopsLeft = system.bundle.schedulerConfig.unscheduleCount
            while (loopsLeft > 0) {
              var executedSomething = pollPool(fj)
              executedSomething ||= executeNow(popMiniQueue())
              if (executedSomething) {
                loopsLeft -= 1
              } else {
                loopsLeft = 0
              }
            }
          case _ =>
        }
      } finally {
        state = ASLEEP
        executeLater(popMiniQueue())
      }
    }
  }

  private[reactors] object ReactorForkJoinWorkerThread { 
    val ASLEEP = new AnyRef {}
    val AWAKE = new AnyRef {}
    val UNSCHEDULING = new AnyRef {}
  }

  private[reactors] class ReactorForkJoinPool extends ForkJoinPool(
    Runtime.getRuntime.availableProcessors,
    new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool) =
        new ReactorForkJoinWorkerThread(pool.asInstanceOf[ReactorForkJoinPool])
    },
    null,
    false
  ) {
    def poll() = pollSubmission()
  }

  /** Default fork/join pool instance used by the default scheduler.
   */
  lazy val defaultForkJoinPool: ForkJoinPool = new ReactorForkJoinPool

  /** Default reactor scheduler.
   */
  lazy val default: Scheduler = new Executed(defaultForkJoinPool)

  /** A scheduler that always starts a reactor on a dedicated thread.
   */
  lazy val newThread: Scheduler = new Dedicated.NewThread(true)

  /** A scheduler that reuses (piggybacks) the current thread to run the reactor.
   *
   *  Until the reactor terminates, the current thread is blocked and cannot be used any
   *  more.
   *  This scheduler cannot be used to start reactors from within another reactor,
   *  and is typically used to turn the application main thread into a reactor.
   *
   *  @see [[org.reactors.Scheduler.Dedicated.Piggyback]]
   */
  lazy val piggyback: Scheduler = new Dedicated.Piggyback()

  /** A `Scheduler` that reuses the target Java `Executor`.
   *
   *  It checks if the specified executor is a `ForkJoinPool` that uses
   *  `ReactorForkJoinWorkerThread` and, if so, applies additional optimizations:
   *
   *  - If `schedule` is called from a `ForkJoinWorkerThread` that belongs to the
   *    `ForkJoinPool` that is the `executor`, then a more lightweight mechanism is
   *    used to schedule the task.
   *  - When a frame completes execution, it calls `unschedule`. This will attempt to
   *    remove submitted tasks from the `ForkJoinPool` a certain of times and execute
   *    them directly. The `scheduler.default.unschedule-count` bundle configuration
   *    key is the maximum number of attempts.  If removing is not successful,
   *    this immediately stops.
   *  - Each `ReactorForkJoinWorkerThread` has an associated mini-queue into which it
   *    stores at most one scheduled `Frame`. Any frame must first be in the mini-queue
   *    before getting converted into a task and sent to the queue. Before any such
   *    worker thread returns control to the pool, it must flush its mini-queue.
   *    Simultaneously, there is a reanimator thread that periodically traverses the
   *    mini-queues of all the threads, and flushes them if necessary.
   *
   *  @param executor       The `Executor` used to schedule reactor tasks.
   *  @param handler        The default error handler for fatal errors not passed to
   *                        reactors.
   */
  class Executed(
    val executor: java.util.concurrent.Executor,
    val handler: Scheduler.Handler = Scheduler.defaultHandler
  ) extends Scheduler {

    def schedule(frame: Frame): Unit = {
      Thread.currentThread match {
        case t: ReactorForkJoinWorkerThread if t.getPool eq executor =>
          t.execute(frame)
        case t: ForkJoinWorkerThread if t.getPool eq executor =>
          val r = frame.schedulerState.asInstanceOf[Runnable]
          ForkJoinTask.adapt(r).fork()
        case _ =>
          executor.execute(frame.schedulerState.asInstanceOf[Runnable])
      }
    }

    override def newState(frame: Frame): Scheduler.State = {
      new Scheduler.State.Default with Runnable {
        def run() = frame.executeBatch()
      }
    }

    override def preschedule(system: ReactorSystem) {
      Thread.currentThread match {
        case t: ReactorForkJoinWorkerThread =>
          t.preschedule()
        case _ =>
          return
      }
    }

    override def unschedule(system: ReactorSystem, throwable: Throwable) {
      Thread.currentThread match {
        case t: ReactorForkJoinWorkerThread =>
          t.unschedule(system, throwable)
        case _ =>
          return
      }
    }
  }

  /** An abstract scheduler that always dedicates a thread to a reactor.
   */
  abstract class Dedicated extends Scheduler {
    def schedule(frame: Frame): Unit = {
      frame.schedulerState.asInstanceOf[Dedicated.Worker].awake()
    }
  }

  /** Contains utility classes and implementations of the dedicated scheduler.
   */
  object Dedicated {

    private[reactors] class Worker(val frame: Frame, val handler: Scheduler.Handler)
    extends Scheduler.State.Default {
      @volatile var thread: Thread = _

      @tailrec final def loop(): Unit = {
        try {
          frame.executeBatch()
          frame.monitor.synchronized {
            while (!frame.hasTerminated && !frame.hasPendingEvents) {
              frame.monitor.wait()
            }
          }
        } catch {
          case t if handler.isDefinedAt(t) =>
            handler(t)
            throw t
        }
        if (!frame.hasTerminated) loop()
      }

      def awake() {
        frame.monitor.synchronized {
          frame.monitor.notify()
        }
      }
    }

    private[reactors] class WorkerThread(val worker: Worker) extends Thread {
      override def run() = worker.loop()
    }

    /** Starts a new dedicated thread for each reactor that is created.
     *
     *  The new thread does not stop until the reactor terminates.
     *  The thread is optionally a daemon thread.
     *
     *  @param isDaemon          Is the new thread a daemon.
     *  @param handler           The error handler for fatal errors not passed to
     *                           reactors.
     */
    class NewThread(
      val isDaemon: Boolean,
      val handler: Scheduler.Handler = Scheduler.defaultHandler
    ) extends Dedicated {

      override def newState(frame: Frame): Dedicated.Worker = {
        val w = new Worker(frame, handler)
        w.thread = new WorkerThread(w)
        w
      }

      override def schedule(frame: Frame): Unit = {
        val t = frame.schedulerState.asInstanceOf[Worker].thread
        if (t.getState == Thread.State.NEW) t.start()
        super.schedule(frame)
      }
    }

    /** Executes the reactor on the thread that called the reactor system's `spawn`
     *  method to create the reactor.
     *
     *  While reactors are generally sent off to some other thread or computer for
     *  execution after the reactor has been created, this scheduler executes the
     *  reactor on the current thread.
     *
     *  The current thread is permanently blocked until the reactor terminates.
     *  Using this scheduler from an existing reactor is illegal and throws an
     *  exception.
     *  This scheduler is meant to be used to turn the application main thread
     *  into a reactor, i.e. to step from the normal multithreaded world into
     *  the reactor universe.
     *
     *  @param handler           The error handler for the fatal errors not passed to
     *                           reactors.
     */
    class Piggyback(val handler: Scheduler.Handler = Scheduler.defaultHandler)
    extends Dedicated {
      override def newState(frame: Frame): Dedicated.Worker = {
        val w = new Worker(frame, handler)
        w
      }

      override def initSchedule(frame: Frame) {
        super.initSchedule(frame)
        if (Reactor.selfAsOrNull != null)
          throw new IllegalStateException(
            "Cannot use piggyback scheduler from within a reactor.")
      }

      override def schedule(frame: Frame) {
        frame.schedulerState match {
          case w: Worker =>
            if (w.thread == null) {
              w.thread = Thread.currentThread
              w.loop()
            } else {
              super.schedule(frame)
            }
        }
      }
    }

  }

  /** Executes the reactor on the timer thread.
   *
   *  The reactor is run every `period` milliseconds.
   *  This is regardless of the number of events in this reactor's event queue.
   *
   *  When the reactor runs, it flushes as many events as there are initially pending
   *  events.
   *
   *  @param period       Period between executing the reactor.
   *  @param isDaemon     Is the timer thread a daemon thread.
   */
  class Timer(
    private val period: Long,
    val isDaemon: Boolean = true,
    val handler: Scheduler.Handler = Scheduler.defaultHandler
  ) extends Scheduler {
    private var timer: java.util.Timer = null
    private val frames = mutable.Set[Frame]()

    def this(period: Long, isDaemon: Boolean) =
      this(period, isDaemon, Scheduler.defaultHandler)

    def shutdown() = if (timer != null) timer.cancel()

    override def newState(frame: Frame) = new Timer.State

    def schedule(frame: Frame) {
      val state = frame.schedulerState.asInstanceOf[Timer.State]
      if (state.task == null) state.synchronized {
        if (state.task == null) {
          state.task = new TimerTask {
            timerTask =>
            def run() {
              try {
                if (frame.hasTerminated) {
                  timerTask.cancel()
                  removeFrame(frame)
                } else {
                  frame.executeBatch()
                  frame.activate()
                }
              } catch handler
            }
          }
          timer.schedule(state.task, period, period)
        }
      }
    }

    override def initSchedule(frame: Frame) {
      super.initSchedule(frame)
      addFrame(frame)
    }

    private def addFrame(frame: Frame) = frames.synchronized {
      frames += frame
      if (frames.size == 1) {
        timer = new java.util.Timer(s"Scheduler-${freshId[Timer]}", isDaemon)
      }
    }

    private def removeFrame(frame: Frame) = frames.synchronized {
      frames -= frame
      if (frames.size == 0) {
        timer.cancel()
        timer = null
      }
    }
  }

  object Timer {
    /** Holds state of frames scheduled by the `Timer` scheduler.
     */
    class State extends Scheduler.State.Default {
      @volatile var task: TimerTask = null

      override def onBatchStart(frame: Frame): Unit = {
        allowedBudget = frame.estimateTotalPendingEvents
      }
    }
  }

}

