package org.reactress



import scala.annotation.tailrec
import scala.util.DynamicVariable
import isolate._



trait Isolate[@spec(Int, Long, Double) T] extends ReactIsolate[T, T] {

  def events: Reactive[T] = source

  def later: Enqueuer[T] = frame.eventQueue

}


object Isolate {

  trait Looper[@spec(Int, Long, Double) T]
  extends Isolate[T] {
    val fallback: Signal[Option[T]]

    def initialize() {
      react <<= system onCase {
        case IsolateStarted | IsolateEmptyQueue => fallback() match {
          case Some(v) => later.enqueueIfEmpty(v)
          case None => channel.seal()
        }
      }
    }

    initialize()
  }

}
