package org.reactress
package isolate



import scala.collection._
import annotation.tailrec



trait Reactor {
  def react[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T]
}


object Reactor {

  abstract class SyncedExecutionService extends Reactor {
    def requestProcessing[@spec(Int, Long, Double) T](i: Isolate.Synced[T]): Unit
    def react[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T] = {
      val isolate = new Isolate.Synced[T](this)
      body(isolate.emitter)
      isolate
    }
  }

  class SyncedExecutor(val executor: java.util.concurrent.Executor) extends SyncedExecutionService {
    def requestProcessing[@spec(Int, Long, Double) T](i: Isolate.Synced[T]) = executor.execute(new Runnable {
      import Isolate.Synced
      
      private var default: T = _

      def run() = {
        var claimed = false
        try {
          i.monitor.synchronized {
            if (i.state != Synced.Claimed) {
              i.state = Synced.Claimed
              claimed = true
            }
          }
          run(10)
        } finally {
          i.monitor.synchronized {
            if (claimed) {
              if (i.eventQueue.nonEmpty) {
                requestProcessing(i)
                i.state = Synced.Requested
              } else {
                i.state = Synced.Idle
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

