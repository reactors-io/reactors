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
 *  @see [[scala.reactive.IsolateSystem]]
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
  def schedule(frame: IsolateFrame[_]): Unit

  /** Initiates the isolate frame.
   *  Clients never call this method directly.
   *
   *  The scheduler can use the `schedulerInfo` field of the isolate frame
   *  to store its custom configuration objects for this isolate frame.
   *  For example, a `Scheduler` based on a Java `Executor` would typically
   *  store a `Runnable` object here and later use it from the `schedule` method.
   *
   *  @param frame      the isolate frame to initiate
   */
  def initiate(frame: IsolateFrame[_]): Unit

  /** The handler for the fatal errors that are not sent to
   *  the `failures` stream of the isolate.
   *
   *  '''Note:'''
   *  If the `failures` reactive throws
   *  while handling any throwables passed to it,
   *  then those throwables are passed to this error handler.
   *  This means that the `handler` can also receive non-fatal errors.
   *
   *  @see [[scala.util.control.NonFatal]]
   */
  def handler: Scheduler.Handler
  
}


/** Companion object for creating standard isolate schedulers.
 */
object Scheduler {

  type Handler = PartialFunction[Throwable, Unit]

  /** The default handler prints the exception to the standard error stream.
   */
  val defaultHandler: Handler = {
    case t: Throwable =>
      Console.err.println(t)
      t.printStackTrace()
  }

  /** Contains a set of schedulers registered with each isolate system.
   */
  class Bundle(val defaultScheduler: Scheduler) {
    private val schedulers = mutable.Map[String, Scheduler]()

    /** Retrieves the scheduler registered under the specified name.
     *  
     *  @param name        the name of the scheduler
     *  @return            the scheduler object associated with the name
     */
    def retrieve(name: String): Scheduler = {
      schedulers(name)
    }
  
    /** Registers the scheduler under a specific name,
     *  so that it can be later retrieved using the 
     *  `retrieve` method.
     *
     *  @param name       the name under which to register the scheduler
     *  @param s          the scheduler object to register
     */
    def register(name: String, s: Scheduler) {
      if (schedulers contains name) sys.error(s"Scheduler $name already registered.")
      else schedulers(name) = s
    }
  }

  /** Scheduler bundle factory methods.
   */
  object Bundle {
    /** A bundle with default schedulers from the `Scheduler` companion object.
     *  
     *  @return           the default scheduler bundle
     */
    def default(default: Scheduler): Bundle = {
      val b = new Bundle(default)
      b.register("scala.reactive.Scheduler.globalExecutionContext", Scheduler.globalExecutionContext)
      b.register("scala.reactive.Scheduler.default", Scheduler.default)
      b.register("scala.reactive.Scheduler.newThread", Scheduler.newThread)
      b.register("scala.reactive.Scheduler.piggyback", Scheduler.piggyback)
      b
    }
  }

  /** Default scheduler bundle.
   */
  lazy val defaultBundle = Bundle.default(Scheduler.default)

  /** Scheduler that shares the global Scala execution context.
   */
  lazy val globalExecutionContext: Scheduler = new Executed(ExecutionContext.Implicits.global)

  /** Default isolate scheduler.
   */
  lazy val default: Scheduler = new Executed(new ForkJoinPool(
    Runtime.getRuntime.availableProcessors,
    new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool) = new ForkJoinWorkerThread(pool) {
        setName(s"IsolateScheduler-${getName}")
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
    def initiate(frame: IsolateFrame[_]): Unit = {
      frame.schedulerInfo = new Runnable {
        def run() = frame.run()
      }
    }

    def schedule(frame: IsolateFrame[_]): Unit = {
      executor.execute(frame.schedulerInfo.asInstanceOf[Runnable])
    }
  }

  /** An abstract scheduler that always dedicates a thread to an isolate.
   */
  abstract class Dedicated extends Scheduler {
    def newWorker(frame: IsolateFrame[_]): Dedicated.Worker

    def schedule(frame: IsolateFrame[_]): Unit = {
      frame.schedulerInfo.asInstanceOf[Dedicated.Worker].awake()
    }

    def initiate(frame: IsolateFrame[_]): Unit = {
      frame.schedulerInfo = newWorker(frame)
    }
  }

  /** Contains utility classes and implementations of the dedicated scheduler.
   */
  object Dedicated {
    private[reactive] class Worker(val frame: IsolateFrame[_], val handler: Scheduler.Handler) {
      val monitor = new util.Monitor

      @tailrec final def loop(f: IsolateFrame[_]): Unit = {
        try {
          frame.run()
          monitor.synchronized {
            while (frame.eventQueue.isEmpty && !frame.isTerminating) monitor.wait()
          }
        } catch handler
        if (frame.isolateState.get != IsolateFrame.Terminated) loop(f)
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
      def newWorker(frame: IsolateFrame[_]) = {
        val w = new Worker(frame, handler)
        val t = new WorkerThread(w)
        t.start()
        w
      }
    }

    /** Executes the isolate on the thread that called the isolate system's `isolate` method
     *  to create the isolate.
     *
     *  While isolates are generally sent off to some other thread or computer for execution
     *  after the isolate has been created, this scheduler executes the isolate on the
     *  current thread.
     *
     *  The current thread is permanently blocked until the isolate terminates.
     *  Using this scheduler from an existing isolate is illegal and throws an exception.
     *  This scheduler is meant to be used to turn the application main thread
     *  into an isolate, i.e. to step from the normal multithreaded world into
     *  the isolate universe.
     *
     *  @param handler           The error handler for the fatal errors not passed to isolates.
     */
    class Piggyback(val handler: Scheduler.Handler = Scheduler.defaultHandler) extends Dedicated {
      def newWorker(frame: IsolateFrame[_]) = {
        val w = new Worker(frame, handler)
        frame.schedulerInfo = w
        w
      }

      override def initiate(frame: IsolateFrame[_]) {
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
   *  @param period       Period between executing the isolate.
   *  @param isDaemon     Is the timer thread a daemon thread.
   */
  class Timer(private val period: Long, val isDaemon: Boolean = true, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends Scheduler {
    private var timer: java.util.Timer = null
    private val frames = mutable.Set[IsolateFrame[_]]()

    def shutdown() = if (timer != null) timer.cancel()

    def schedule(frame: IsolateFrame[_]) {}

    def initiate(frame: IsolateFrame[_]) {
      addFrame(frame)

      timer.schedule(new java.util.TimerTask {
        timerTask =>
        def run() {
          try {
            def notTerm = frame.isolateState.get != IsolateFrame.Terminated

            frame.allowedBudget = frame.eventQueue.size
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

    private def addFrame(frame: IsolateFrame[_]) = frames.synchronized {
      frames += frame
      if (frames.size == 1) {
        timer = new java.util.Timer(s"TimerScheduler-${util.freshId[Timer]}", isDaemon)
      }
    }

    private def removeFrame(frame: IsolateFrame[_]) = frames.synchronized {
      frames -= frame
      if (frames.size == 0) {
        timer.cancel()
        timer = null
      }
    }

  }

}

