package org.reactress
package isolate



import scala.collection._
import scala.annotation.tailrec




abstract class SyncedScheduler extends Scheduler {
  def requestProcessing[@spec(Int, Long, Double) T](i: SyncedPlaceholder[T]): Unit
}


object SyncedScheduler {

  class Executor(val executor: java.util.concurrent.Executor, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends SyncedScheduler {
    def requestProcessing[@spec(Int, Long, Double) T](i: SyncedPlaceholder[T]) = {
      executor.execute(i.work)
    }

    def schedule[@spec(Int, Long, Double) T: Arrayable, I <: Isolate[T]](channels: Reactive[Reactive[T]])(newIsolate: Reactive[T] => I): I = {
      val holder = new SyncedPlaceholder(this, channels, newIsolate)
      holder.isolate.asInstanceOf[I]
    }
  }

  abstract class PerThread extends SyncedScheduler {
    trait WaitingWorker {
      val monitor = new AnyRef
      @volatile var workRequested = false

      def shouldTerminate: Boolean

      @tailrec final def waitAndWork(i: SyncedPlaceholder[_]) {
        monitor.synchronized {
          while (!workRequested) monitor.wait()
        }
        i.work.run()
        if (!shouldTerminate) waitAndWork(i)
      }
    }

    def requestProcessing[@spec(Int, Long, Double) T](i: SyncedPlaceholder[T]) = {
      val t = i.info.asInstanceOf[WaitingWorker]
      t.monitor.synchronized {
        t.workRequested = true
        t.monitor.notify()
      }
    }
  }

  class NewThread(isDaemon: Boolean, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends PerThread {
    private class SyncedThread(i: SyncedPlaceholder[_]) extends Thread with WaitingWorker {
      setDaemon(isDaemon)

      def shouldTerminate = i.eventQueue.isEmpty && i.shouldTerminate

      final override def run() {
        waitAndWork(i)
      }
    }

    def newWaitingWorker(i: SyncedPlaceholder[_]): WaitingWorker = {
      val t = new SyncedThread(i)
      t.start()
      t
    }

    def schedule[@spec(Int, Long, Double) T: Arrayable, I <: Isolate[T]](channels: Reactive[Reactive[T]])(newIsolate: Reactive[T] => I): I = {
      val holder = new SyncedPlaceholder[T](this, channels, newIsolate)
      holder.info = newWaitingWorker(holder)
      holder.isolate.asInstanceOf[I]
    }

  }

  class Piggyback(val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends PerThread {
    def newWaitingWorker(i: SyncedPlaceholder[_]): WaitingWorker = new WaitingWorker {
      def shouldTerminate = i.eventQueue.isEmpty && i.shouldTerminate
    }

    def schedule[@spec(Int, Long, Double) T: Arrayable, I <: Isolate[T]](channels: Reactive[Reactive[T]])(newIsolate: Reactive[T] => I): I = {
      val holder = new SyncedPlaceholder[T](this, channels, newIsolate)
      val worker = newWaitingWorker(holder)
      holder.info = worker
      worker.waitAndWork(holder)
      holder.isolate.asInstanceOf[I]
    }
  }

}
