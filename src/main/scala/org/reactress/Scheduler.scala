package org.reactress



import java.util.concurrent._
import scala.collection._
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec



trait Scheduler {

  def schedule[@spec(Int, Long, Double) T: Arrayable](newIsolate: =>Isolate[T]): Channel[T]

  def handler: Scheduler.Handler

  final def runnableInIsolate(r: Runnable, i: Isolate[_]) = new Runnable {
    def run() {
      if (Isolate.selfIsolate.get != null) {
        throw new IllegalStateException("Cannot execute isolate inside of another isolate.")
      }
      try {
        Isolate.selfIsolate.set(i)
        r.run()
      } catch handler
      finally {
        Isolate.selfIsolate.set(null)
      }
    }
  }
}


object Scheduler {

  type Handler = PartialFunction[Throwable, Unit]

  val defaultHandler: Handler = {
    case t: Throwable =>
      Console.err.println(t)
      t.printStackTrace()
  }

  lazy val globalExecutionContext: Scheduler = new isolate.SyncedScheduler.Executor(ExecutionContext.Implicits.global)
  lazy val default: Scheduler = new isolate.SyncedScheduler.Executor(new ForkJoinPool(
    Runtime.getRuntime.availableProcessors,
    new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool) = new ForkJoinWorkerThread(pool) {
        setName(s"IsolateScheduler-${getName}")
      }
    },
    null,
    true
  ))
  lazy val newThread: Scheduler = new isolate.SyncedScheduler.NewThread(true)
  lazy val piggyback: Scheduler = new isolate.SyncedScheduler.Piggyback()

  object Implicits {
    implicit lazy val globalExecutionContext = Scheduler.globalExecutionContext
    implicit lazy val default = Scheduler.default
    implicit lazy val newThread = Scheduler.newThread
    implicit lazy val piggyback = Scheduler.piggyback
  }

}

