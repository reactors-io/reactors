package org.reactress



import scala.annotation.tailrec
import scala.util.DynamicVariable
import isolate._



trait Isolate[@spec(Int, Long, Double) T] extends ReactIsolate[T, T] {

  def later: Enqueuer[T] = frame.eventQueue

}


object Isolate {

  trait Looper[@spec(Int, Long, Double) T]
  extends Isolate[T] {
    val fallback: Signal[Option[T]]

    def initialize() {
      react <<= sysEvents onCase {
        case IsolateStarted | IsolateEmptyQueue => fallback() match {
          case Some(v) => later.enqueueIfEmpty(v)
          case None => channel.seal()
        }
      }
    }

    initialize()
  }

  /** Returns the current isolate.
   *
   *  If the caller is not executing in an isolate,
   *  throws an `IllegalStateException`.
   *
   *  The caller must specify the type of the current isolate
   *  if the type of the isolate is required.
   *
   *  @tparam I      the type of the current isolate
   */
  def self[I <: Isolate[_]]: I = {
    val i = ReactIsolate.selfIsolate.get
    if (i == null) throw new IllegalStateException(s"${Thread.currentThread.getName} not executing in an isolate.")
    i.asInstanceOf[I]
  }

}
