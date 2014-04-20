package org.reactress



import scala.collection._
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec



trait Scheduler {
  def schedule[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T]

  def handler: Scheduler.Handler

  final def runnableInIsolate(i: Isolate[_], r: Runnable) = new Runnable {
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
      println(t)
      t.printStackTrace()
  }

  lazy val globalExecutionContext: Scheduler = new isolate.SyncedScheduler.Executor(ExecutionContext.Implicits.global)
  lazy val default: Scheduler = new isolate.SyncedScheduler.Executor(new java.util.concurrent.ForkJoinPool)
  lazy val newThread: Scheduler = new isolate.SyncedScheduler.NewThread(true)
  lazy val piggyback: Scheduler = new isolate.SyncedScheduler.Piggyback()

  object Implicits {
    implicit lazy val globalExecutionContext = Scheduler.globalExecutionContext
    implicit lazy val default = Scheduler.default
  }

}

