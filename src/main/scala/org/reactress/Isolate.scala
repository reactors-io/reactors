package org.reactress



import scala.annotation.tailrec



trait Isolate[@spec(Int, Long, Double) T] extends ReactRecord {
  private[reactress] val sysEventsEmitter = new Reactive.Emitter[Isolate.SysEvent]

  final def sysEvents: Reactive[Isolate.SysEvent] = sysEventsEmitter
}


object Isolate {

  sealed trait SysEvent
  case object Start extends SysEvent
  case object EmptyEventQueue extends SysEvent
  case object Terminate extends SysEvent

  sealed trait State
  case object Created extends State
  case object Running extends State
  case object Terminated extends State

  private[reactress] val selfIsolate = new ThreadLocal[Isolate[_]] {
    override def initialValue = null
  }

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
    if (i == null) throw new IllegalStateException("${Thread.currentThread.getName} not in an isolate.")
    i.asInstanceOf[Isolate[T]]
  }

  /** Creates a looper isolate using a target scheduler.
   *
   *  @tparam T          type of the new isolate
   *  @param loopevent   the code to generate the event when the queue is empty
   *  @param body        the body of the current isolate - maps the event source to custom code
   *  @param s           the scheduler for the looper isolate
   */
  def looper[@spec(Int, Long, Double) T: Arrayable](loopEvent: =>T)(body: Reactive[T] => Unit)(implicit s: Scheduler): Isolate[T] = {
    val emitter = new Reactive.SynchronizedEmitter[T]
    s.schedule(Reactive.single(emitter))(src => new Looper(src, emitter, () => loopEvent, body))
  }

  class Looper[@spec(Int, Long, Double) T: Arrayable]
    (src: Reactive[T], emitter: Reactive.SynchronizedEmitter[T], loopEvent: () => T, body: Reactive[T] => Unit)
  extends Isolate[T] {
    def stop() = emitter.close()

    react <<= sysEvents onEvent {
      case Isolate.EmptyEventQueue =>
        emitter += loopEvent()
    }

    val currentIsolate = selfIsolate.get
    try {
      selfIsolate.set(this)
      body(src)
    } finally {
      selfIsolate.set(currentIsolate)
    }
  }

  object Looper {
    def self[@spec(Int, Long, Double) T]: Looper[T] = Isolate.self.asInstanceOf[Looper[T]]
  }

}
