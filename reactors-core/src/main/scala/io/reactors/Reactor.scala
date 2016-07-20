package io.reactors



import scala.annotation.tailrec
import scala.collection._
import scala.util.DynamicVariable
import io.reactors.concurrent._



/** A reactor is a basic unit of concurrency.
 *
 *  A concurrent program in the Reactors framework is composed of multiple reactors,
 *  which execute concurrently, and in isolation. The only way they can communicate is
 *  by exchanging events using entities called *channels*.
 *
 *  A `Reactor[T]` object accepts events of type `T` on its input channel.
 *  One reactor can propagate events concurrently to other reactors.
 *  Event streams cannot be shared between reactors --
 *  it is an error to use an event stream originating in one reactor
 *  in some different reactor.
 *
 *  Reactors are defined by extending the `Reactor` trait.
 *  The events passed to reactors can be subscribed to using
 *  their `main.events` stream.
 *  Here is an example:
 *
 *  {{{
 *  class MyPrinter extends Reactor[String] {
 *    main.events onEvent {
 *      e => println(e)
 *    }
 *  }
 *  }}}
 *
 *  Separate reactor instances that exist at runtime are created using reactor systems.
 *  Each reactor belongs to a specific reactor system. Usually there is a single reactor
 *  system within a single program instance.
 *  Here is an example:
 *
 *  {{{
 *  val reactorSystem = ReactorSystem.default("MyReactorSystem")
 *  val channel = reactorSystem.spawn(Proto[MyPrinter])
 *  }}}
 *
 *  Creating a reactor returns its channel.
 *  Events can be sent to a channel using the `!` method:
 *
 *  {{{
 *  channel ! "Hi!" // eventually, this is printed by `MyPrinter`
 *  }}}
 *
 *  To stop, a reactor must seal its main channel.
 *  The following reactor seals its main channel after receiving the first event:
 *
 *  {{{
 *  class MyPrinter extends Reactor[String] {
 *    main.events onEvent {
 *      e =>
 *      println(e)
 *      main.seal()
 *    }
 *  }
 *  }}}
 *
 *  Reactors also receive special `SysEvent` events on the `sysEvents` stream.
 *  
 *  @tparam T        the type of events, which `this` reactor produces
 */
trait Reactor[@spec(Int, Long, Double) T] {
  @volatile private[reactors] var frame: Frame = _

  private def illegal() =
    throw new IllegalStateException("Only reactor systems can create reactors.")

  /* start workaround for a handful of specialization bugs */

  private def init(dummy: Reactor[T]) {
    frame = Reactor.selfFrame.get match {
      case null => illegal()
      case f => f.asInstanceOf[Frame]
    }
    frame.reactor = this
    Reactor.selfReactor.set(this)
  }

  init(this)

  /* end workaround */

  /** The unique id of this reactor.
   *
   *  @return          the unique id, assigned only to this reactor
   */
  final def uid: Long = frame.uid

  /** The reactor system of this reactor.
   */
  final def system: ReactorSystem = frame.reactorSystem

  /** The main connector of this reactor.
   */
  final def main: Connector[T] = {
    frame.defaultConnector.asInstanceOf[Connector[T]]
  }

  /** The system event stream of this isolate.
   */
  final def sysEvents: Events[SysEvent] = frame.sysEmitter

}


object Reactor {

  private[reactors] val selfReactor = new ThreadLocal[Reactor[_]] {
    override def initialValue = null
  }

  private[reactors] val selfFrame = new ThreadLocal[Frame] {
    override def initialValue = null
  }

  /** Returns the current reactor.
   *
   *  If the caller is not executing in a reactor, throws an `IllegalStateException`.
   *
   *  The caller must specify the type of the current reactor
   *  if the type of the reactor is required.
   *
   *  @tparam I      the type of the current reactor
   *  @return        the current reactor
   */
  def selfAs[I <: Reactor[_]]: I = {
    val i = selfReactor.get
    if (i == null)
      throw new IllegalStateException(
        s"${Thread.currentThread.getName} not executing in a reactor.")
    i.asInstanceOf[I]
  }

  /** Returns the current reactor, or `null`.
   *
   *  The caller must specify the type of the current reactor
   *  if the type of the reactor is required.
   *
   *  @tparam I      the type of the current reactor
   *  @return        the current reactor, or `null`
   */
  def selfAsOrNull[I <: Reactor[_]]: I = selfReactor.get.asInstanceOf[I]

  /** Returns the current reactor that produces events of type `T`.
   */
  def self[@specialized(Int, Long, Double) T]: Reactor[T] = Reactor.selfAs[Reactor[T]]

  /** Convenience class for anonymous reactor declarations.
   *
   *  Serves as a placeholder for cyclic declarations.
   */
  class Placeholder

  private[reactors] class Abstract[T] extends Reactor[T]

  /** Creates a reactor proto from a closure.
   *
   *  This is a short-hand for creating a reactor template.
   *
   *  @tparam T       type of the main event stream
   *  @param body     reactor body
   */
  def apply[@specialized(Int, Long, Double) T](
    body: Reactor[T] => Unit
  ): Proto[Reactor[T]] = {
    Proto[AnonymousReactor[T]](body)
  }

}
