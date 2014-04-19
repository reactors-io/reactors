package org.reactress
package isolate



import scala.collection._
import annotation.tailrec




abstract class SyncedScheduler extends Scheduler {
  def requestProcessing[@spec(Int, Long, Double) T](i: SyncedIsolate[T]): Unit
  def schedule[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T] = {
    val isolate = new SyncedIsolate[T](this)
    body(isolate.emitter)
    isolate
  }
}


object SyncedScheduler {

  class Executor(val executor: java.util.concurrent.Executor) extends SyncedScheduler {
    def requestProcessing[@spec(Int, Long, Double) T](i: SyncedIsolate[T]) = executor.execute(new Runnable {
      private var default: T = _

      def run() = {
        var claimed = false
        try {
          i.monitor.synchronized {
            if (i.state != SyncedIsolate.Claimed) {
              i.state = SyncedIsolate.Claimed
              claimed = true
            }
          }
          run(10)
        } finally {
          i.monitor.synchronized {
            if (claimed) {
              if (i.eventQueue.nonEmpty) {
                requestProcessing(i)
                i.state = SyncedIsolate.Requested
              } else {
                i.state = SyncedIsolate.Idle
              }
            }
          }
        }
      }

      @tailrec def run(n: Int) {
        var event = default
        var shouldEmit = false
        i.monitor.synchronized {
          if (i.eventQueue.nonEmpty) {
            event = i.eventQueue.dequeue()
            shouldEmit = true
          }
        }
        if (shouldEmit) i.emitter += event
        if (n > 0) run(n - 1)
      }
    })
  }

}
