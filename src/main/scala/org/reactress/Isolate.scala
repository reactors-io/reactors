package org.reactress



import scala.annotation.tailrec
import scala.util.DynamicVariable
import isolate._



trait Isolate[@spec(Int, Long, Double) T] extends ReactIsolate[T, T] {

  def events: Reactive[T] = source

  def later: Enqueuer[T] = frame.eventQueue

}


object Isolate {
}
