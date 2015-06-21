package scala.reactive



import java.util.concurrent._
import scala.collection._
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec
import isolate._



/** An object that schedules isolates for execution.
 *
 *  After an isolate is instantiated, its isolate frame is assigned a scheduler by the isolate system.
 *  An isolate that is assigned a specific scheduler will always be executed on that same scheduler.
 *
 *  After creating an isolate, every isolate system will first call the `initiate` method on the isolate frame.
 *  Then, the isolate system will call the `schedule` method every time there are events ready for the isolate.
 *
 *  '''Note:'''
 *  Clients never invoke `Scheduler` operations directly,
 *  but can implement their own scheduler if necessary.
 *
 *  @see [[scala.reactive.IsoSystem]]
 */
trait Scheduler {

  /** Schedules an isolate frame for execution.
   *  Clients never call this method directly.
   *
   *  This method uses the isolate frame to flush messages from its event queue
   *  and propagate events through the isolate.
   *
   *  @param frame      the isolate frame to schedule
   */
  def schedule(frame: IsoFrame): Unit

  /** Initiates the isolate frame.
   *  Clients never call this method directly.
   *
   *  @param frame      the isolate frame to initiate
   */
  def initiate(frame: IsoFrame): Unit

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
  def handler: Scheduler.Handler

  /** Creates an `Info` object for the isolate frame.
   *
   *  @param frame       the isolate frame
   *  @return            creates a fresh scheduler info object
   */
  def newInfo(frame: IsoFrame): Scheduler.Info = new Scheduler.Info.Default

}


/** Companion object for creating standard isolate schedulers.
 */
object Scheduler {

  /** Superclass for the information objects that a scheduler attaches to an isolate frame.
   */
  abstract class Info {
    /** Called when scheduling starts.
     *  
     *  @param frame    the isolate frame
     */
    def onBatchStart(frame: IsoFrame): Unit = {
    }

    /** Checks whether the isolate can process more events.
     *  
     *  @return         `true` if more events can be scheduled
     */
    def canSchedule: Boolean

    /** Called just before an event gets scheduled.
     *  
     *  @param frame    the isolate frame
     */
    def onBatchEvent(frame: IsoFrame): Unit

    /** Called when scheduling stops.
     *  
     *  @param frame    the isolate frame
     */
    def onBatchStop(frame: IsoFrame): Unit = {
    }

    /** Picks and dequeues a single event from the dequeuer set of the associated isolate.
     */
    def dequeueEvent(frame: IsoFrame): Unit = {
      frame.multiplexer.dequeueEvent()
    }
  }

  object Info {
    /** The default info object implementation.
     */
    class Default extends Info {
      @volatile var allowedBudget: Long = _
      @volatile var interruptRequested: Boolean = _

      override def onBatchStart(frame: IsoFrame): Unit = {
        allowedBudget = 50
      }

      def canSchedule = allowedBudget > 0

      def onBatchEvent(frame: IsoFrame): Unit = {
        allowedBudget -= 1
      }

      override def onBatchStop(frame: IsoFrame): Unit = {
        interruptRequested = false
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

  /** Scheduler that shares the global Scala execution context.
   */
  lazy val globalExecutionContext: Scheduler = new Executed(ExecutionContext.Implicits.global)

  /** Default isolate scheduler.
   */
  lazy val default: Scheduler = new Executed(new ForkJoinPool(
    Runtime.getRuntime.availableProcessors,
    new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool) = new ForkJoinWorkerThread(pool) {
        setName(s"IsoScheduler-${getName}")
      }
    },
    null,
    true
  ))

  /** A scheduler that always starts an isolate on a dedicated thread.
   */
  lazy val newThread: Scheduler = new Dedicated.NewThread(true)

  /** A scheduler that reuses (piggybacks) the current thread to run the isolate.
   *
   *  Until the isolate terminates, the current thread is blocked and cannot be used any more.
   *  This scheduler cannot be used to start isolates from within another isolate,
   *  and is typically used to turn the application main thread into an isolate.
   *
   *  @see [[scala.reactive.Scheduler.Dedicated.Piggyback]]
   */
  lazy val piggyback: Scheduler = new Dedicated.Piggyback()

  /** A `Scheduler` that reuses the target Java `Executor`.
   *
   *  @param executor       The `Executor` used to schedule isolate tasks.
   *  @param handler        The default error handler for fatal errors not passed to isolates.
   */
  class Executed(val executor: java.util.concurrent.Executor, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends Scheduler {
    def initiate(frame: IsoFrame): Unit = {
    }

    def schedule(frame: IsoFrame): Unit = {
      executor.execute(frame.schedulerInfo.asInstanceOf[Runnable])
    }

    override def newInfo(frame: IsoFrame): Scheduler.Info = {
      new Scheduler.Info.Default with Runnable {
        def run() = frame.run()
      }
    }
  }

  /** An abstract scheduler that always dedicates a thread to an isolate.
   */
  abstract class Dedicated extends Scheduler {
    def schedule(frame: IsoFrame): Unit = {
      frame.schedulerInfo.asInstanceOf[Dedicated.Worker].awake()
    }

    def initiate(frame: IsoFrame): Unit = {
    }
  }

  /** Contains utility classes and implementations of the dedicated scheduler.
   */
  object Dedicated {
    private[reactive] class Worker(val frame: IsoFrame, val handler: Scheduler.Handler)
    extends Scheduler.Info.Default {
      val monitor = new util.Monitor

      @tailrec final def loop(f: IsoFrame): Unit = {
        try {
          frame.run()
          monitor.synchronized {
            while (frame.multiplexer.areEmpty && !frame.multiplexer.isTerminated) {
              monitor.wait()
            }
          }
        } catch handler
        if (!frame.isTerminated) loop(f)
      }

      def awake() {
        monitor.synchronized {
          monitor.notify()
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
     *  @param handler           The error handler for fatal errors not passed to isolates.
     */
    class NewThread(val isDaemon: Boolean, val handler: Scheduler.Handler = Scheduler.defaultHandler)
    extends Dedicated {
      override def newInfo(frame: IsoFrame): Dedicated.Worker = {
        val w = new Worker(frame, handler)
        w
      }

      override def initiate(frame: IsoFrame): Unit = {
        val w = frame.schedulerInfo.asInstanceOf[Worker]
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
    class Piggyback(val handler: Scheduler.Handler = Scheduler.defaultHandler)
    extends Dedicated {
      override def newInfo(frame: IsoFrame): Dedicated.Worker = {
        val w = new Worker(frame, handler)
        w
      }

      override def initiate(frame: IsoFrame) {
        // ride, piggy, ride, like you never rode before!
        super.initiate(frame)
        frame.schedulerInfo.asInstanceOf[Worker].loop(frame)
      }
    }

  }

  /** Executes the isolate on the timer thread.
   *
   *  The isolate is run every `period` milliseconds.
   *  This is regardless of the number of events in this isolate's event queue.
   *
   *  When the isolate runs, it flushes as many events as there are initially pending events.
   *
   *  @param period       Period between executing the isolate.
   *  @param isDaemon     Is the timer thread a daemon thread.
   */
  class Timer(private val period: Long, val isDaemon: Boolean = true, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends Scheduler {
    private var timer: java.util.Timer = null
    private val frames = mutable.Set[IsoFrame]()

    def shutdown() = if (timer != null) timer.cancel()

    override def newInfo(frame: IsoFrame): Scheduler.Info = new Scheduler.Info.Default {
      override def onBatchStart(frame: IsoFrame): Unit = {
        allowedBudget = frame.multiplexer.totalSize
      }
    }

    def schedule(frame: IsoFrame) {}

    def initiate(frame: IsoFrame) {
      addFrame(frame)

      timer.schedule(new java.util.TimerTask {
        timerTask =>
        def run() {
          try {
            def notTerm = frame.isolateState.get != IsoFrame.Terminated

            if (notTerm) frame.run()
            else {
              timerTask.cancel()
              removeFrame(frame)
            }

            frame.tryOwn()
          } catch handler
        }
      }, period, period)
    }

    private def addFrame(frame: IsoFrame) = frames.synchronized {
      frames += frame
      if (frames.size == 1) {
        timer = new java.util.Timer(s"TimerScheduler-${util.freshId[Timer]}", isDaemon)
      }
    }

    private def removeFrame(frame: IsoFrame) = frames.synchronized {
      frames -= frame
      if (frames.size == 0) {
        timer.cancel()
        timer = null
      }
    }

  }

}

