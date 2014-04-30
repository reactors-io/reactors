package org.reactress



import scala.annotation.tailrec
import scala.util.DynamicVariable
import isolate._



trait Isolate[@spec(Int, Long, Double) T] extends ReactRecord {
  private[reactress] val sysEventsEmitter = new Reactive.Emitter[Isolate.SysEvent]
  private[reactress] var frame: IsolateFrame[T] = _

  private def illegal() = throw new IllegalStateException("Only schedulers can create isolates.")

  /* start workaround for a handful of specialization bugs */

  private def init(dummy: Isolate[T]) {
    frame = Isolate.argFrame.value match {
      case null => illegal()
      case eq => eq.asInstanceOf[IsolateFrame[T]]
    }
    Isolate.selfIsolate.set(this)
  }

  init(this)

  private[reactress] final def eventQueue = frame.eventQueue

  /* end workaround */

  final def self: this.type = this

  final def sysEvents: Reactive[Isolate.SysEvent] = sysEventsEmitter

  final def source: Reactive[T] = frame.eventQueue

  final def later: Enqueuer[T] = frame.eventQueue

  final def channel: Channel[T] = frame.channel

}


object Isolate {

  sealed trait SysEvent
  case object Start extends SysEvent
  case object EmptyQueue extends SysEvent
  case class Failed(throwable: Throwable) extends SysEvent
  case object Terminate extends SysEvent

  sealed trait State
  case object Created extends State
  case object Running extends State
  case object Terminated extends State

  private[reactress] val selfIsolate = new ThreadLocal[Isolate[_]] {
    override def initialValue = null
  }

  private[reactress] val argFrame = new DynamicVariable[IsolateFrame[_]](null)

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
  def self[@spec(Int, Long, Double) T]: Isolate[T] = {
    val i = selfIsolate.get
    if (i == null) throw new IllegalStateException(s"${Thread.currentThread.getName} not in an isolate.")
    i.asInstanceOf[Isolate[T]]
  }

  trait Looper[@spec(Int, Long, Double) T]
  extends Isolate[T] {
    val fallback: Signal[Option[T]]

    def initialize() {
      val feedback = new Reactive.Emitter[T]
      channel.attach(feedback)
      react <<= sysEvents onCase {
        case Isolate.Start | Isolate.EmptyQueue =>
          if (fallback().nonEmpty) feedback += fallback().get
          else {
            feedback.close()
            channel.seal()
          }
      }
    }
    initialize()
  }

  object Looper {
    def self[@spec(Int, Long, Double) T]: Looper[T] = Isolate.self.asInstanceOf[Looper[T]]
  }

}
