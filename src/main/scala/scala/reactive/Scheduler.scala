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
   *  @tparam Q         the type of the isolate event queue
   *  @param frame      the isolate frame to schedule
   */
  def schedule[@spec(Int, Long, Double) T](frame: IsolateFrame[T]): Unit

  /** Initiates the isolate frame.
   *  Clients never call this method directly.
   *
   *  The scheduler can use the `schedulerInfo` field of the isolate frame
   *  to store its custom configuration objects for this isolate frame.
   *  For example, a `Scheduler` based on a Java `Executor` would typically
   *  store a `Runnable` object here and later use it from the `schedule` method.
   *
   *  @tparam Q         the type of the isolate event queue
   *  @param frame      the isolate frame to initiate
   */
  def initiate[@spec(Int, Long, Double) T](frame: IsolateFrame[T]): Unit

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

  /** Implicit scheduler objects - importing one of the implicit schedulers
   *  into a certain scope makes it available for all the newly started isolates.
   */
  object Implicits {
    implicit lazy val globalExecutionContext = Scheduler.globalExecutionContext
    implicit lazy val default = Scheduler.default
    implicit lazy val newThread = Scheduler.newThread
    implicit lazy val piggyback = Scheduler.piggyback
  }

  /** A `Scheduler` that reuses the target Java `Executor`.
   *
   *  @param executor       The `Executor` used to schedule isolate tasks.
   *  @param handler        The default error handler for fatal errors not passed to isolates.
   */
  class Executed(val executor: java.util.concurrent.Executor, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends Scheduler {
    def initiate[@spec(Int, Long, Double) T](frame: IsolateFrame[T]): Unit = {
      frame.schedulerInfo = new Runnable {
        def run() = frame.run(frame.dequeuer)
      }
    }

    def schedule[@spec(Int, Long, Double) T](frame: IsolateFrame[T]): Unit = {
      executor.execute(frame.schedulerInfo.asInstanceOf[Runnable])
    }
  }

  /** An abstract scheduler that always dedicates a thread to an isolate.
   */
  abstract class Dedicated extends Scheduler {
    def newWorker[@spec(Int, Long, Double) T](frame: IsolateFrame[T]): Dedicated.Worker[T]

    def schedule[@spec(Int, Long, Double) T](frame: IsolateFrame[T]): Unit = {
      frame.schedulerInfo.asInstanceOf[Dedicated.Worker[T]].awake()
    }

    def initiate[@spec(Int, Long, Double) T](frame: IsolateFrame[T]): Unit = {
      frame.schedulerInfo = newWorker(frame)
    }
  }

  /** Contains utility classes and implementations of the dedicated scheduler.
   */
  object Dedicated {
    private[reactive] class Worker[@spec(Int, Long, Double) T](val frame: IsolateFrame[T]) {
      val monitor = new util.Monitor

      @tailrec final def loop(f: IsolateFrame[T]) {
        frame.run(frame.dequeuer)
        monitor.synchronized {
          while (frame.eventQueue.isEmpty && !frame.isTerminating) monitor.wait()
        }
        if (frame.isolateState.get != IsolateFrame.Terminated) loop(f)
      }

      def awake() {
        monitor.synchronized {
          monitor.notify()
        }
      }
    }

    private[reactive] class WorkerThread[@spec(Int, Long, Double) T](val worker: Worker[T]) extends Thread {
      override def run() = worker.loop(worker.frame)
    }

    /** Starts a new dedicated thread for each isolate that is started.
     *
     *  The new thread does not stop until the isolate terminates.
     *  The thread is optionally a daemon thread.
     *
     *  @param isDaemon          Is the new thread a daemon.
     *  @param handler           The error handler for fatal errors not passed to isolates.
     */
    class NewThread(val isDaemon: Boolean, val handler: Scheduler.Handler = Scheduler.defaultHandler)
    extends Dedicated {
      def newWorker[@spec(Int, Long, Double) T](frame: IsolateFrame[T]) = {
        val w = new Worker(frame)
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
      def newWorker[@spec(Int, Long, Double) T](frame: IsolateFrame[T]) = {
        val w = new Worker(frame)
        frame.schedulerInfo = w
        w
      }

      override def initiate[@spec(Int, Long, Double) T](frame: IsolateFrame[T]) {
        // ride, piggy, ride, like you never rode before!
        super.initiate(frame)
        frame.schedulerInfo.asInstanceOf[Worker[T]].loop(frame)
      }
    }
  }

}

