package org.reactress



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
 *  After creating an isolate, every isolate system will first call the initiate method on the isolate frame.
 *  Then, the isolate system will call the `schedule` method every time there are events ready for the isolate.
 *
 *  '''Note:'''
 *  Clients never invoke `Scheduler` operations directly,
 *  but can implement their own scheduler if necessary.
 *
 *  @see [[org.reactress.IsolateSystem]]
 */
trait Scheduler {

  /** Schedules an isolate frame for execution.
   *  Clients never call this method directly.
   *
   *  This method uses the isolate frame that was previously assigned
   *
   *  @tparam Q         the type of the isolate event queue
   *  @param frame      the isolate frame to schedule
   */
  def schedule[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]): Unit

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
  def initiate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]): Unit

  /** The handler for the fatal errors that are not sent to
   *  the `failures` stream of the isolate.
   *
   *  @see [[scala.util.control.NonFatal]]
   */
  def handler: Scheduler.Handler
  
}


/** Companion object for creating standard isolate schedulers.
 */
object Scheduler {

  type Handler = PartialFunction[Throwable, Unit]

  val defaultHandler: Handler = {
    case t: Throwable =>
      Console.err.println(t)
      t.printStackTrace()
  }

  lazy val globalExecutionContext: Scheduler = new Executed(ExecutionContext.Implicits.global)
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
  lazy val newThread: Scheduler = new Dedicated.NewThread(true)
  lazy val piggyback: Scheduler = new Dedicated.Piggyback()

  object Implicits {
    implicit lazy val globalExecutionContext = Scheduler.globalExecutionContext
    implicit lazy val default = Scheduler.default
    implicit lazy val newThread = Scheduler.newThread
    implicit lazy val piggyback = Scheduler.piggyback
  }

  class Executed(val executor: java.util.concurrent.Executor, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends Scheduler {
    def initiate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]): Unit = {
      frame.schedulerInfo = new Runnable {
        def run() = frame.run(frame.dequeuer)
      }
    }

    def schedule[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]): Unit = {
      executor.execute(frame.schedulerInfo.asInstanceOf[Runnable])
    }
  }

  abstract class Dedicated extends Scheduler {
    def newWorker[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]): Dedicated.Worker[T, Q]

    def schedule[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]): Unit = {
      frame.schedulerInfo.asInstanceOf[Dedicated.Worker[T, Q]].awake()
    }

    def initiate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]): Unit = {
      frame.schedulerInfo = newWorker(frame)
    }
  }

  object Dedicated {
    private[reactress] class Worker[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](val frame: IsolateFrame[T, Q]) {
      val monitor = new util.Monitor

      @tailrec final def loop(f: IsolateFrame[T, Q]) {
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

    private[reactress] class WorkerThread[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](val worker: Worker[T, Q]) extends Thread {
      override def run() = worker.loop(worker.frame)
    }

    class NewThread(val isDaemon: Boolean, val handler: Scheduler.Handler = Scheduler.defaultHandler)
    extends Dedicated {
      def newWorker[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]) = {
        val w = new Worker(frame)
        val t = new WorkerThread(w)
        t.start()
        w
      }
    }

    class Piggyback(val handler: Scheduler.Handler = Scheduler.defaultHandler) extends Dedicated {
      def newWorker[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]) = {
        val w = new Worker(frame)
        frame.schedulerInfo = w
        w
      }

      override def initiate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](frame: IsolateFrame[T, Q]) {
        // ride, piggy, ride, like you never rode before!
        super.initiate(frame)
        frame.schedulerInfo.asInstanceOf[Worker[T, Q]].loop(frame)
      }
    }
  }

}

