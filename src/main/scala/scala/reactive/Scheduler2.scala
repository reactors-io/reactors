package scala.reactive



import java.util.concurrent._
import scala.collection._
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec
import isolate._



/** An object that schedules isolates for execution.
 *
 *  After an isolate is instantiated, its isolate frame is assigned a scheduler by the
 *  isolate system.
 *  An isolate that is assigned a specific scheduler will always be executed on that
 *  same scheduler.
 *
 *  After creating an isolate, every isolate system will first call the `startSchedule`
 *  method on the isolate frame.
 *  Then, the isolate system will call the `schedule` method every time there are events
 *  ready for the isolate.
 *
 *  '''Note:'''
 *  Clients never invoke `Scheduler2` operations directly,
 *  but can implement their own scheduler if necessary.
 *
 *  @see [[scala.reactive.IsoSystem]]
 */
trait Scheduler2 {

  /** Notifies an isolate frame that it should be executed.
   *  Clients never call this method directly.
   *
   *  This method uses the isolate frame to flush messages from its event queue
   *  and propagate events through the isolate.
   *
   *  @param frame      the isolate frame to schedule
   */
  def schedule(frame: Frame): Unit

  /** Tells the scheduler to start listening to schedule requests for the isolate frame.
   *  Clients never call this method directly.
   *
   *  By default, assigns the default scheduler state to the `schedulerState` field in
   *  the isolate frame.
   *
   *  @param frame      the isolate frame to start scheduling
   */
  def startSchedule(frame: Frame): Unit = {
    frame.schedulerState = newState(frame)
  }

  /** The handler for the fatal errors that are not sent to
   *  the `failures` stream of the isolate.
   *
   *  '''Note:'''
   *  If the `failures` event stream throws
   *  while handling any throwables passed to it,
   *  then those throwables are passed to this error handler.
   *  This means that the `handler` can also receive non-fatal errors.
   *
   *  @see [[scala.util.control.NonFatal]]
   */
  def handler: Scheduler2.Handler

  /** Creates an `State` object for the isolate frame.
   *
   *  @param frame       the isolate frame
   *  @return            creates a fresh scheduler info object
   */
  protected def newState(frame: Frame): Scheduler2.State = new Scheduler2.State.Default

}


/** Companion object for creating standard isolate schedulers.
 */
object Scheduler2 {

  /** Superclass for the information objects that a scheduler attaches to an isolate
   *  frame.
   */
  abstract class State {
    /** Called when a batch of events are about to be handled.
     *  
     *  @param frame    the isolate frame
     */
    def onBatchStart(frame: Frame): Unit = {
    }

    /** Checks whether the isolate can process more events.
     *  
     *  @return         `true` if more events can be scheduled
     */
    def canConsume: Boolean

    /** Called just before an event gets scheduled.
     *  
     *  @param frame    the isolate frame
     */
    def onBatchEvent(frame: Frame): Unit

    /** Called when scheduling stops.
     *  
     *  @param frame    the isolate frame
     */
    def onBatchStop(frame: Frame): Unit = {
    }
  }

  object State {
    /** The default info object implementation.
     */
    class Default extends State {
      @volatile var allowedBudget: Long = _

      override def onBatchStart(frame: Frame): Unit = {
        allowedBudget = 50
      }

      def canConsume = allowedBudget > 0

      def onBatchEvent(frame: Frame): Unit = {
        allowedBudget -= 1
      }

      override def onBatchStop(frame: Frame): Unit = {
      }
    }
  }

  type Handler = PartialFunction[Throwable, Unit]

  /** The default handler prints the exception to the standard error stream.
   */
  val defaultHandler: Handler = {
    case t: Throwable =>
      Console.err.println(t)
      t.printStackTrace()
  }

  /** Scheduler2 that shares the global Scala execution context.
   */
  lazy val globalExecutionContext: Scheduler2 =
    new Executed(ExecutionContext.Implicits.global)

  /** Default isolate scheduler.
   */
  lazy val default: Scheduler2 = new Executed(new ForkJoinPool(
    Runtime.getRuntime.availableProcessors,
    new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool) = new ForkJoinWorkerThread(pool) {
        setName(s"Scheduler2-${getName}")
      }
    },
    null,
    true
  ))

  /** A scheduler that always starts an isolate on a dedicated thread.
   */
  lazy val newThread: Scheduler2 = new Dedicated.NewThread(true)

  /** A scheduler that reuses (piggybacks) the current thread to run the isolate.
   *
   *  Until the isolate terminates, the current thread is blocked and cannot be used any
   *  more.
   *  This scheduler cannot be used to start isolates from within another isolate,
   *  and is typically used to turn the application main thread into an isolate.
   *
   *  @see [[scala.reactive.Scheduler2.Dedicated.Piggyback]]
   */
  lazy val piggyback: Scheduler2 = new Dedicated.Piggyback()

  /** A `Scheduler2` that reuses the target Java `Executor`.
   *
   *  @param executor       The `Executor` used to schedule isolate tasks.
   *  @param handler        The default error handler for fatal errors not passed to
   *                        isolates.
   */
  class Executed(
    val executor: java.util.concurrent.Executor,
    val handler: Scheduler2.Handler = Scheduler2.defaultHandler
  ) extends Scheduler2 {

    def schedule(frame: Frame): Unit = {
      executor.execute(frame.schedulerState.asInstanceOf[Runnable])
    }

    override def newState(frame: Frame): Scheduler2.State = {
      new Scheduler2.State.Default with Runnable {
        def run() = frame.executeBatch()
      }
    }

  }

  /** An abstract scheduler that always dedicates a thread to an isolate.
   */
  abstract class Dedicated extends Scheduler2 {
    def schedule(frame: Frame): Unit = {
      frame.schedulerState.asInstanceOf[Dedicated.Worker].awake()
    }
  }

  /** Contains utility classes and implementations of the dedicated scheduler.
   */
  object Dedicated {

    private[reactive] class Worker(val frame: Frame, val handler: Scheduler2.Handler)
    extends Scheduler2.State.Default {

      @tailrec final def loop(f: Frame): Unit = {
        try {
          frame.executeBatch()
          frame.monitor.synchronized {
            while (!frame.hasTerminated && !frame.hasPendingEvents) {
              frame.monitor.wait()
            }
          }
        } catch handler
        if (!frame.hasTerminated) loop(f)
      }

      def awake() {
        frame.monitor.synchronized {
          frame.monitor.notify()
        }
      }
    }

    private[reactive] class WorkerThread(val worker: Worker) extends Thread {
      override def run() = worker.loop(worker.frame)
    }

    /** Starts a new dedicated thread for each isolate that is created.
     *
     *  The new thread does not stop until the isolate terminates.
     *  The thread is optionally a daemon thread.
     *
     *  @param isDaemon          Is the new thread a daemon.
     *  @param handler           The error handler for fatal errors not passed to
     *                           isolates.
     */
    class NewThread(
      val isDaemon: Boolean,
      val handler: Scheduler2.Handler = Scheduler2.defaultHandler
    ) extends Dedicated {

      override def newState(frame: Frame): Dedicated.Worker = {
        val w = new Worker(frame, handler)
        w
      }

      override def startSchedule(frame: Frame): Unit = {
        super.startSchedule(frame)
        val w = frame.schedulerState.asInstanceOf[Worker]
        val t = new WorkerThread(w)
        t.start()
      }

    }

    /** Executes the isolate on the thread that called the isolate system's `isolate`
     *  method to create the isolate.
     *
     *  While isolates are generally sent off to some other thread or computer for
     *  execution after the isolate has been created, this scheduler executes the
     *  isolate on the current thread.
     *
     *  The current thread is permanently blocked until the isolate terminates.
     *  Using this scheduler from an existing isolate is illegal and throws an
     *  exception.
     *  This scheduler is meant to be used to turn the application main thread
     *  into an isolate, i.e. to step from the normal multithreaded world into
     *  the isolate universe.
     *
     *  @param handler           The error handler for the fatal errors not passed to
     *                           isolates.
     */
    class Piggyback(val handler: Scheduler2.Handler = Scheduler2.defaultHandler)
    extends Dedicated {
      override def newState(frame: Frame): Dedicated.Worker = {
        val w = new Worker(frame, handler)
        w
      }

      override def startSchedule(frame: Frame) {
        // ride, piggy, ride, like you never rode before!
        super.startSchedule(frame)
        frame.schedulerState.asInstanceOf[Worker].loop(frame)
      }
    }

  }

  /** Executes the isolate on the timer thread.
   *
   *  The isolate is run every `period` milliseconds.
   *  This is regardless of the number of events in this isolate's event queue.
   *
   *  When the isolate runs, it flushes as many events as there are initially pending
   *  events.
   *
   *  @param period       Period between executing the isolate.
   *  @param isDaemon     Is the timer thread a daemon thread.
   */
  class Timer(
    private val period: Long,
    val isDaemon: Boolean = true,
    val handler: Scheduler2.Handler = Scheduler2.defaultHandler
  ) extends Scheduler2 {
    private var timer: java.util.Timer = null
    private val frames = mutable.Set[Frame]()

    def shutdown() = if (timer != null) timer.cancel()

    override def newState(frame: Frame) = new Scheduler2.State.Default {
      override def onBatchStart(frame: Frame): Unit = {
        allowedBudget = frame.estimateTotalPendingEvents
      }
    }

    def schedule(frame: Frame) {}

    override def startSchedule(frame: Frame) {
      super.startSchedule(frame)
      addFrame(frame)

      val task = new java.util.TimerTask {
        timerTask =>
        def run() {
          try {
            frame.executeBatch()
            if (frame.hasTerminated) {
              timerTask.cancel()
              removeFrame(frame)
            }

            // we put into the "executing" state to be consistent,
            // although this is not strictly necessary
            frame.scheduleForExecution()
          } catch handler
        }
      }

      timer.schedule(task, period, period)
    }

    private def addFrame(frame: Frame) = frames.synchronized {
      frames += frame
      if (frames.size == 1) {
        timer = new java.util.Timer(s"Scheduler2-${util.freshId[Timer]}", isDaemon)
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

}

