package org.reactress



import scala.collection._
import annotation.tailrec



trait Scheduler {
  def schedule[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T]

  def handler: Scheduler.Handler

  final def caughtExceptions(r: Runnable) = new Runnable {
    def run() {
      try r.run()
      catch handler
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

}

