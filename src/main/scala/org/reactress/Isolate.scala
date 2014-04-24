package org.reactress



import scala.annotation.tailrec
import scala.util.DynamicVariable



trait Isolate[@spec(Int, Long, Double) T] extends ReactRecord {
  private[reactress] val sysEventsEmitter = new Reactive.Emitter[Isolate.SysEvent]
  @volatile private[reactress] var eventQueueSpec: EventQueue[T] = _
  @volatile private[reactress] var channelSpec: Channel[T] = _

  private def illegal() = throw new IllegalStateException("Only schedulers can create isolates.")

  /* start workaround for a handful of specialization bugs */

  private def init(dummy: Isolate[T]) {
    eventQueueSpec = Isolate.argEventQueue.value match {
      case null => illegal()
      case eq => eq.asInstanceOf[EventQueue[T]]
    }
    channelSpec = Isolate.argChannel.value match {
      case null => illegal()
      case ch => ch.asInstanceOf[Channel[T]]
    }
    Isolate.selfIsolate.set(this)
  }
  init(this)

  private[reactress] final def eventQueue = eventQueueSpec

  /* end workaround */

  private[reactress] def propagate() = eventQueue.dequeue()

  final def self: this.type = this

  final def sysEvents: Reactive[Isolate.SysEvent] = sysEventsEmitter

  final def source: Reactive[T] = eventQueue

  final def later: Enqueuer[T] = eventQueue

  final def channel: Channel[T] = channelSpec

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

  private[reactress] val argEventQueue = new DynamicVariable[EventQueue[_]](null)

  private[reactress] val argChannel = new DynamicVariable[Channel[_]](null)

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
