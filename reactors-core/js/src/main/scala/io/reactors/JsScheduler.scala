package io.reactors



import io.reactors.concurrent.Frame
import scala.concurrent.ExecutionContext



object JsScheduler {
  class GlobalQueue extends Scheduler {
    def schedule(frame: Frame): Unit = {
      val r = frame.schedulerState.asInstanceOf[Runnable]
      ExecutionContext.Implicits.global.execute(r)
    }

    override def newState(frame: Frame): Scheduler.State = {
      new Scheduler.State.Default with Runnable {
        def run() = {
          try frame.executeBatch()
          catch {
            case t: Throwable =>
              frame.reactorSystem.errorHandler(t)
          }
        }
      }
    }
  }

  lazy val default: Scheduler = new GlobalQueue

  object Key {
    val default = "org.reactors.JsScheduler::default"
    def defaultScheduler = default
  }
}
