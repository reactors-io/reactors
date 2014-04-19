package org.reactress



import scala.collection._
import annotation.tailrec



trait Scheduler {
  def schedule[@spec(Int, Long, Double) T: Arrayable](body: Reactive[T] => Unit): Isolate[T]
}


object Scheduler {
}

