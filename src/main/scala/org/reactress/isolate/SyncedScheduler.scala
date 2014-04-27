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
    def requestProcessing[@spec(Int, Long, Double) T](p: SyncedPlaceholder[T]) = {
      executor.execute(p.work)
    }

    def schedule[@spec(Int, Long, Double) T: Arrayable](newIsolate: =>Isolate[T]) = {
      val holder = new SyncedPlaceholder(this, () => newIsolate, null)
      requestProcessing(holder)
      holder.channel
    }
  }

  abstract class PerThread extends SyncedScheduler {
    trait WaitingWorker {
      @volatile var workRequested = false
      @volatile var placeHolder: SyncedPlaceholder[_] = null

      def shouldTerminate: Boolean

      final def waitAndWork(p: SyncedPlaceholder[_]) {
        @tailrec def repeat() {
          p.monitor.synchronized {
            if (p.isolate.eventQueue.isEmpty && !shouldTerminate) workRequested = false
            while (!workRequested) p.monitor.wait()
          }
          p.work.run()
          if (!shouldTerminate) repeat()
        }

        p.work.run()
        repeat()
      }
    }

    def requestProcessing[@spec(Int, Long, Double) T](p: SyncedPlaceholder[T]) = {
      val t = p.worker.asInstanceOf[WaitingWorker]
      p.monitor.synchronized {
        t.workRequested = true
        p.monitor.notify()
      }
    }
  }

  class NewThread(isDaemon: Boolean, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends PerThread {
    class WaitingWorkerThread extends Thread with WaitingWorker {
      setName(s"IsolateThread-${getId}")
      setDaemon(isDaemon)

      def shouldTerminate = placeHolder.isolate.eventQueue.isEmpty && placeHolder.shouldTerminate

      final override def run() {
        waitAndWork(placeHolder)
      }
    }

    def newWaitingWorker(): WaitingWorkerThread = {
      val t = new WaitingWorkerThread()
      t
    }

    def schedule[@spec(Int, Long, Double) T: Arrayable](newIsolate: =>Isolate[T]) = {
      val worker = newWaitingWorker()
      val holder = new SyncedPlaceholder[T](this, () => newIsolate, worker)
      worker.placeHolder = holder
      worker.start()
      holder.channel
    }

  }

  class Piggyback(val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends PerThread {
    def newWaitingWorker(): WaitingWorker = new WaitingWorker {
      def shouldTerminate = placeHolder.isolate.eventQueue.isEmpty && placeHolder.shouldTerminate
    }

    def schedule[@spec(Int, Long, Double) T: Arrayable](newIsolate: =>Isolate[T]) = {
      val worker = newWaitingWorker()
      val holder = new SyncedPlaceholder[T](this, () => newIsolate, worker)
      worker.placeHolder = holder
      worker.waitAndWork(holder)
      holder.channel
    }
  }

}
