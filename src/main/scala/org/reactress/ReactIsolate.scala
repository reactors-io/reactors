package org.reactress



import scala.annotation.tailrec
import scala.util.DynamicVariable
import isolate._



trait ReactIsolate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q] extends ReactRecord {
  private[reactress] var frame: IsolateFrame[T, Q] = _

  private def illegal() = throw new IllegalStateException("Only schedulers can create isolates.")

  /* start workaround for a handful of specialization bugs */

  private def init(dummy: ReactIsolate[T, Q]) {
    frame = ReactIsolate.argFrame.value match {
      case null => illegal()
      case eq => eq.asInstanceOf[IsolateFrame[T, Q]]
    }
    ReactIsolate.selfIsolate.set(this)
  }

  init(this)

  /* end workaround */

  final def self: this.type = this

  final def source: Reactive[Q] = frame.sourceEmitter

  final def failures: Reactive[Throwable] = frame.failureEmitter

  def later: Enqueuer[T]

  final def channel: Channel[T] = frame.channel

}


object ReactIsolate {

  sealed trait State
  case object Created extends State
  case object Running extends State
  case object Terminated extends State

  private[reactress] val selfIsolate = new ThreadLocal[ReactIsolate[_, _]] {
    override def initialValue = null
  }

  private[reactress] val argFrame = new DynamicVariable[IsolateFrame[_, _]](null)

  /** Returns the current isolate.
   *
   *  If the caller is not executing in an isolate,
   *  throws an `IllegalStateException`.
   *
   *  The caller must specify the type of the current isolate
   *  if the type of the isolate is required.
   *
   *  @tparam T    the type parameter of the current isolate
   */
  def self[I <: ReactIsolate[_, _]]: I = {
    val i = selfIsolate.get
    if (i == null) throw new IllegalStateException(s"${Thread.currentThread.getName} not executing in an isolate.")
    i.asInstanceOf[I]
  }

}
