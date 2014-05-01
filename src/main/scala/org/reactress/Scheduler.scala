package org.reactress



import java.util.concurrent._
import scala.collection._
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec
import isolate._



trait Scheduler {

  def schedule[@spec(Int, Long, Double) Q](frame: IsolateFrame[_, Q], d: Dequeuer[Q]): Unit

  def initiate[@spec(Int, Long, Double) Q](frame: IsolateFrame[_, Q]): Unit = {}

  def handler: Scheduler.Handler
  
}


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
    def schedule[@spec(Int, Long, Double) Q](frame: IsolateFrame[_, Q], d: Dequeuer[Q]): Unit = {
      if (frame.schedulerInfo == null) frame.schedulerInfo = new Runnable {
        def run() = frame.run(d)
      }
      executor.execute(frame.schedulerInfo.asInstanceOf[Runnable])
    }
  }

  abstract class Dedicated extends Scheduler {
    def newWorker[@spec(Int, Long, Double) Q](frame: IsolateFrame[_, Q], d: Dequeuer[Q]): Dedicated.Worker[Q]

    def schedule[@spec(Int, Long, Double) Q](frame: IsolateFrame[_, Q], d: Dequeuer[Q]): Unit = {
      if (frame.schedulerInfo == null) frame.schedulerInfo = newWorker(frame, d)
      frame.schedulerInfo.asInstanceOf[Dedicated.Worker[Q]].awake()
    }
  }

  object Dedicated {
    private[reactress] class Worker[@spec(Int, Long, Double) Q](val frame: IsolateFrame[_, Q], val dequeuer: Dequeuer[Q]) {
      val monitor = new util.Monitor

      @tailrec final def loop(f: IsolateFrame[_, Q]) {
        frame.run(dequeuer)
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

    private[reactress] class WorkerThread[@spec(Int, Long, Double) Q](val worker: Worker[Q]) extends Thread {
      override def run() = worker.loop(worker.frame)
    }

    class NewThread(val isDaemon: Boolean, val handler: Scheduler.Handler = Scheduler.defaultHandler)
    extends Dedicated {
      def newWorker[@spec(Int, Long, Double) Q](frame: IsolateFrame[_, Q], d: Dequeuer[Q]) = {
        val w = new Worker(frame, d)
        val t = new WorkerThread(w)
        t.start()
        w
      }
    }

    class Piggyback(val handler: Scheduler.Handler = Scheduler.defaultHandler) extends Dedicated {
      def newWorker[@spec(Int, Long, Double) Q](frame: IsolateFrame[_, Q], d: Dequeuer[Q]) = {
        val w = new Worker(frame, d)
        frame.schedulerInfo = w
        w
      }

      override def initiate[@spec(Int, Long, Double) Q](frame: IsolateFrame[_, Q]) {
        // ride, piggy, ride, like you never rode before!
        frame.schedulerInfo.asInstanceOf[Worker[Q]].loop(frame)
      }
    }
  }

}

