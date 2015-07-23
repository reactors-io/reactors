package scala.reactive



import scala.annotation.tailrec
import scala.collection._
import scala.util.DynamicVariable
import isolate._



/** An isolated, independent event propagation context.
 *
 *  An `Iso[T]` object accepts events of type `T` on its input channel.
 *  One isolate can propagate events concurrently to other isolates --
 *  it is a basic element of concurrency.
 *  Event streams cannot be shared between isolates --
 *  it is an error to use an event stream originating in one isolate
 *  in some different isolate.
 *
 *  Isolates are defined by extending the `Iso` trait.
 *  The events passed to isolates can be subscribed to using
 *  their `events` stream.
 *  Here is an example:
 *
 *  {{{
 *  class MyPrinter extends Iso[String] {
 *    react <<= events onEvent {
 *      e => println(e)
 *    }
 *  }
 *  }}}
 *
 *  Separate isolate instances that exist at runtime
 *  are created using isolate systems.
 *  Here is an example:
 *
 *  {{{
 *  val isolateSystem = IsoSystem.default("MyIsolateSystem")
 *  val channel = isolateSystem.isolate(Proto[MyPrinter])
 *  }}}
 *
 *  Creating an isolate returns its channel.
 *  Event streams can be attached to channels to propagate their events to isolates.
 *  TODO: update this documentation, as it is now obsolete.
 *
 *  {{{
 *  val emitter = new Events.Emitter[String]
 *  channel.attach(emitter)
 *  emitter += "Hi!" // eventually, this is printed by `MyPrinter`
 *  }}}
 *
 *  To stop an isolate, its channel needs to be sealed, 
 *  and all the previously attached event streams need to be closed.
 *
 *  {{{
 *  emitter.close()
 *  channel.seal()
 *  }}}
 *
 *  Isolates also receive special `SysEvent`s on the `sysEvents` event stream.
 *  If a subscription on the `events` event stream throws a non-lethal exception,
 *  the exception is emitted on the `failures` event stream.
 *  
 *  @tparam T        the type of the events this isolate produces
 */
trait Iso[@spec(Int, Long, Double) T] {
  @volatile private[reactive] var frame: Frame = _
  @volatile private[reactive] var eventSources: mutable.Set[EventSource] = _
  @volatile private[reactive] var sysEventSub: Events.Subscription = _
  private[reactive] val sysEmitter = new Events.Emitter[SysEvent]
  val implicits = new Iso.Implicits

  private def illegal() =
    throw new IllegalStateException("Only isolate systems can create isolates.")

  /* start workaround for a handful of specialization bugs */

  private def init(dummy: Iso[T]) {
    frame = Iso.selfFrame.get match {
      case null => illegal()
      case eq => eq.asInstanceOf[Frame]
    }
    frame.iso = this
    eventSources = mutable.Set[EventSource]()
    sysEventSub = internal.events.foreach(x => sysEmitter react x)
    Iso.selfIso.set(this)
  }

  init(this)

  /* end workaround */

  /** The unique id of this isolate.
   *
   *  @return          the unique id, assigned only to this isolate
   */
  final def uid: Long = frame.uid

  /** The isolate system of this isolate.
   */
  final def system: IsoSystem = frame.isolateSystem

  /** The main connector of this isolate.
   */
  final def main: Connector[T] = {
    frame.defaultConnector.asInstanceOf[Connector[T]]
  }

  /** The system connector of this isolate, which is a daemon.
   */
  private def internal: Connector[SysEvent] = {
    frame.internalConnector.asInstanceOf[Connector[SysEvent]]
  }

  /** The system event stream of this isolate.
   */
  final def sysEvents: Events[SysEvent] = sysEmitter

}


object Iso {

  private[reactive] val selfIso = new ThreadLocal[Iso[_]] {
    override def initialValue = null
  }

  private[reactive] val selfFrame = new ThreadLocal[Frame] {
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
   *  @tparam I      the type of the current isolate
   *  @return        the current isolate
   */
  def self[I <: Iso[_]]: I = {
    val i = selfIso.get
    if (i == null)
      throw new IllegalStateException(
        s"${Thread.currentThread.getName} not executing in an isolate.")
    i.asInstanceOf[I]
  }

  /** Returns the current isolate, or `null`.
   *
   *  The caller must specify the type of the current isolate
   *  if the type of the isolate is required.
   *
   *  @tparam I      the type of the current isolate
   *  @return        the current isolate, or `null`
   */
  def selfOrNull[I <: Iso[_]]: I = selfIso.get.asInstanceOf[I]

  /** Returns the current isolate that produces events of type `T`.
   */
  def of[@specialized(Int, Long, Double) T]: Iso[T] = Iso.self[Iso[T]]

  class Implicits {
    implicit val canLeak: CanLeak = Permission.newCanLeak
  }

  def canLeak: CanLeak = selfIso.get match {
    case null =>
      sys.error("Iso.Implicits.canLeak cannot be used outside an isolate")
    case iso =>
      iso.implicits.canLeak
  }

}
