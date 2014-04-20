package org.reactress
package isolate



import scala.collection._
import scala.annotation.tailrec




abstract class SyncedScheduler extends Scheduler {
  def requestProcessing[@spec(Int, Long, Double) T](i: SyncedIsolate[T]): Unit
}


object SyncedScheduler {

  class Executor(val executor: java.util.concurrent.Executor, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends SyncedScheduler {
    def requestProcessing[@spec(Int, Long, Double) T](i: SyncedIsolate[T]) = {
      executor.execute(i.work)
    }

    def schedule[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T] = {
      val isolate = new SyncedIsolate[T](this)
      body(isolate.emitter)
      isolate
    }
  }

  abstract class PerThread extends SyncedScheduler {
    trait WaitingWorker {
      val monitor = new AnyRef
      @volatile var terminationRequested = false
      @volatile var workRequested = false

      @tailrec final def waitAndWork(i: SyncedIsolate[_]) {
        monitor.synchronized {
          while (!workRequested) monitor.wait()
        }
        i.work.run()
        if (!terminationRequested) waitAndWork(i)
      }
    }

    def requestProcessing[@spec(Int, Long, Double) T](i: SyncedIsolate[T]) = {
      val t = i.info.asInstanceOf[WaitingWorker]
      t.monitor.synchronized {
        t.workRequested = true
        t.monitor.notify()
      }
    }
  }

  class NewThread(isDaemon: Boolean, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends PerThread {
    private class SyncedThread(i: SyncedIsolate[_]) extends Thread with WaitingWorker {
      setDaemon(isDaemon)

      final override def run() {
        waitAndWork(i)
      }
    }

    def newWaitingWorker(i: SyncedIsolate[_]): WaitingWorker = {
      val t = new SyncedThread(i)
      t.start()
      t
    }

    def schedule[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T] = {
      val isolate = new SyncedIsolate[T](this)
      isolate.info = newWaitingWorker(isolate)
      body(isolate.emitter)
      isolate
    }

  }

  class Piggyback(val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends PerThread {
    def newWaitingWorker(i: SyncedIsolate[_]): WaitingWorker = new WaitingWorker {}

    def schedule[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T] = {
      val isolate = new SyncedIsolate[T](this)
      val worker = newWaitingWorker(isolate)
      isolate.info = worker
      body(isolate.emitter)
      worker.waitAndWork(isolate)
      isolate
    }
  }

}
