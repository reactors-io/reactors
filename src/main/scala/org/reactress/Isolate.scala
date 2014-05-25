package org.reactress



import scala.annotation.tailrec
import scala.util.DynamicVariable
import isolate._



/** An isolated, independent event propagation context.
 *
 *  An `Isolate[T]` object accepts events of type `T` on its input channel.
 *  One isolate can propagate events concurrently to other isolates --
 *  it is a basic element of concurrency.
 *  Reactive values cannot be shared between isolates --
 *  it is an error to use a reactive value originating in one isolate
 *  in some different isolate.
 *
 *  Isolates are defined by extending the `Isolate` trait.
 *  The events passed to isolates can be subscribed to using
 *  their `source` reactive.
 *  Here is an example:
 *
 *  {{{
 *  class MyPrinter extends Isolate[String] {
 *    react <<= source onEvent {
 *      e => println(e)
 *    }
 *  }
 *  }}}
 *
 *  Separate isolate instances that exist at runtime
 *  are created using isolate systems.
 *  The `isolate` method in the isolate system requires a scheduler
 *  to execute the isolate.
 *  Here is an example:
 *
 *  {{{
 *  import Scheduler.Implicits.globalExecutionContext
 *  val isolateSystem = IsolateSystem.default("MyIsolateSystem")
 *  val channel = isolateSystem.isolate(Proto[MyPrinter])
 *  }}}
 *
 *  Creating an isolate returns its channel.
 *  Reactives can be attached to channels to propagate their events to isolates.
 *
 *  {{{
 *  val emitter = new Reactive.Emitter[String]
 *  channel.attach(emitter)
 *  emitter += "Hi!" // eventually, this is printed by `MyPrinter`
 *  }}}
 *
 *  To stop an isolate, its channel needs to be sealed, 
 *  and all the previously attached reactives need to be closed.
 *
 *  {{{
 *  emitter.close()
 *  channel.seal()
 *  }}}
 *
 *  Isolates also receive special `SysEvent`s on the `sysEvents` reactive.
 *  If a subscription on the `source` reactive throws a non-fatal exception,
 *  the exception is emitted on the `failures` reactive.
 *  
 *  @tparam T        the type of the events this isolate produces
 */
trait Isolate[@spec(Int, Long, Double) T] extends ReactRecord {
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

  /* end workaround */

  final def system: IsolateSystem = frame.isolateSystem

  final def sysEvents: Reactive[SysEvent] = frame.systemEmitter

  final def source: Reactive[T] = frame.sourceEmitter

  final def failures: Reactive[Throwable] = frame.failureEmitter

  final def channel: Channel[T] = frame.channel

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
   *  @tparam I      the type of the current isolate
   */
  def self[I <: Isolate[_]]: I = {
    val i = selfIsolate.get
    if (i == null) throw new IllegalStateException(s"${Thread.currentThread.getName} not executing in an isolate.")
    i.asInstanceOf[I]
  }

}
