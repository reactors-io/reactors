package org.reactress
package isolate



import scala.collection._




abstract class SyncedScheduler extends Scheduler {
  def requestProcessing[@spec(Int, Long, Double) T](i: SyncedIsolate[T]): Unit
  def schedule[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T] = {
    val isolate = new SyncedIsolate[T](this)
    body(isolate.emitter)
    isolate
  }
}


object SyncedScheduler {

  class Executor(val executor: java.util.concurrent.Executor, val handler: Scheduler.Handler = Scheduler.defaultHandler)
  extends SyncedScheduler {
    def requestProcessing[@spec(Int, Long, Double) T](i: SyncedIsolate[T]) = {
      executor.execute(i.work)
    }
  }

}
