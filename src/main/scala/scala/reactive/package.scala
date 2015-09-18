package scala



import scala.annotation.implicitNotFound
import scala.collection._
import scala.reflect.ClassTag



package object reactive {

  type spec = specialized

  type XY = Long

  implicit class XYExtensions(val v: XY) extends AnyVal {
    final def x = XY.xOf(v)
    final def y = XY.yOf(v)

    override def toString = "XY(%d, %d)".format(x, y)
  }

  object XY {
    final def xOf(v: Long) = (v & 0xffffffff).toInt
    final def yOf(v: Long) = (v >>> 32).toInt
    final def apply(x: Int, y: Int): XY = value(x, y)
    final def value(x: Int, y: Int): Long = (y.toLong << 32) | (x.toLong & 0xffffffffL)
    final def invalid = (Int.MinValue.toLong << 32) | ((Int.MinValue >>> 1).toLong << 1)
    final def add(thiz: XY, that: XY) = XY(thiz.x + that.x, thiz.y + that.y)
    final def diff(thiz: XY, that: XY) = XY(thiz.x - that.x, thiz.y - that.y)
    final def mult(thiz: XY, v: Int) = XY(thiz.x * v, thiz.y * v)
  }

  type RCell[T] = container.RCell[T]

  val RCell = container.RCell

  type ReactRecord = container.ReactRecord

  val ReactRecord = container.ReactRecord

  type RContainer[T] = container.RContainer[T]

  val RContainer = container.RContainer

  type RBuilder[T, Repr] = container.RBuilder[T, Repr]

  val RBuilder = container.RBuilder

  type RArray[T] = container.RArray[T]

  val RArray = container.RArray

  type RQueue[T] = container.RQueue[T]

  val RQueue = container.RQueue

  type RUnrolledQueue[T] = container.RUnrolledQueue[T]

  val RUnrolledQueue = container.RUnrolledQueue

  type RPriorityQueue[T] = container.RPriorityQueue[T]

  val RPriorityQueue = container.RPriorityQueue

  type RBinaryHeap[T] = container.RBinaryHeap[T]

  val RBinaryHeap = container.RBinaryHeap

  type RSet[T] = container.RSet[T]

  val RSet = container.RSet

  type RHashSet[T] = container.RHashSet[T]

  val RHashSet = container.RHashSet

  type RMap[K, V <: AnyRef] = container.RMap[K, V]

  val RMap = container.RMap

  type RHashValMap[K, V] = container.RHashValMap[K, V]

  val RHashValMap = container.RHashValMap

  type RHashMap[K, V >: Null <: AnyRef] = container.RHashMap[K, V]

  val RHashMap = container.RHashMap

  type RCatamorph[T, S] = container.RCatamorph[T, S]

  val RCatamorph = container.RCatamorph

  type MonoidCatamorph[T, S] = container.MonoidCatamorph[T, S]

  val MonoidCatamorph = container.MonoidCatamorph

  type CommuteCatamorph[T, S] = container.CommuteCatamorph[T, S]

  val CommuteCatamorph = container.CommuteCatamorph

  type AbelianCatamorph[T, S] = container.AbelianCatamorph[T, S]

  val AbelianCatamorph = container.AbelianCatamorph

  type RTileMap[T] = container.RTileMap[T]

  val RTileMap = container.RTileMap

  type SignalCatamorph[T] = container.SignalCatamorph[T]

  val SignalCatamorph = container.SignalCatamorph

  /* random */

  type ReactRandom = calc.ReactRandom

  /* calculations */

  type Monoid[T] = calc.Monoid[T]

  val Monoid = calc.Monoid

  type Commutoid[T] = calc.Commutoid[T]

  val Commutoid = calc.Commutoid

  type Abelian[T] = calc.Abelian[T]

  val Abelian = calc.Abelian

  trait Hash[@spec(Int, Long, Double) T] {
    def apply(x: T): Int
  }

  private class IntHash extends Hash[Int] {
    def apply(x: Int) = x
  }

  implicit val intHash: Hash[Int] = new IntHash

  private class LongHash extends Hash[Long] {
    def apply(x: Long) = (x ^ (x >>> 32)).toInt
  }

  implicit val longHash: Hash[Long] = new LongHash

  private class DoubleHash extends Hash[Double] {
    def apply(x: Double) = {
      val b = java.lang.Double.doubleToLongBits(x)
      (b ^ (b >>> 32)).toInt
    }
  }

  implicit val doubleHash: Hash[Double] = new DoubleHash

  private class RefHash[T <: AnyRef] extends Hash[T] {
    def apply(x: T) = x.##
  }

  implicit def refHash[T <: AnyRef]: Hash[T] = new RefHash[T]

  trait Foreach[@spec(Int, Long, Double) T] {
    def foreach[@spec(Int, Long, Double) U](f: T => U): Unit
  }

  private[reactive] def nextPow2(num: Int): Int = {
    var v = num - 1
    v |= v >> 1
    v |= v >> 2
    v |= v >> 4
    v |= v >> 8
    v |= v >> 16
    v + 1
  }

  /** An implicit value that permits invoking operations that
   *  can buffer events unboundedly.
   * 
   *  Users that are sure their events will not be buffered forever
   *  should import `Permission.canBuffer`.
   */
  @implicitNotFound("This operation can buffer events, and may consume an " +
    "arbitrary amount of memory. " +
    "Import the value `Permission.canBuffer` to allow buffering operations.")
  sealed trait CanBeBuffered

  @implicitNotFound("Calling certain methods can result in time and memory leaks " +
    "when the reference to the corresponding Subscription object is lost. " +
    "If you are sure you want to risk this, import " +
    "implicits.canLeak or scala.reactive.Iso.canLeak from within an isolate, " +
    "or instantiate a fresh canLeak object if calling outside of an isolate. " +
    "Otherwise, consider using observe or foreach.")
  sealed trait CanLeak extends Events.Subscription {
    val leakySubscriptions = mutable.Set[Events.Subscription]()

    /** Unsubscribes all the subscriptions, and removes them from this object. */
    def unsubscribe() = {
      val copiedLeakySubscriptions = leakySubscriptions.toList
      for (s <- copiedLeakySubscriptions) {
        s.unsubscribe()
      }
      leakySubscriptions.clear()
    }
  }

  /** Explicitly importing this object permits calling various methods.
   */
  object Permission {
    /** Importing this value permits calling event stream combinators
     *  that can potentially unboundedly buffer events.
     */
    implicit val canBuffer = new CanBeBuffered {}
    def newCanLeak: CanLeak = new CanLeak {}
  }

  /* exception handling */

  case class IsolateError(msg: String, cause: Throwable) extends Error(msg, cause)

  def isLethal(t: Throwable): Boolean = t match {
    case e: VirtualMachineError => true
    case e: LinkageError => true
    case e: IsolateError => true
    case _ => false
  }

  object Lethal {
    def unapply(t: Throwable): Option[Throwable] = t match {
      case e: VirtualMachineError => Some(e)
      case e: LinkageError => Some(e)
      case e: IsolateError => Some(e)
      case _ => None
    }
  }

  def isNonLethal(t: Throwable): Boolean = !isLethal(t)

  object NonLethal {
    def unapply(t: Throwable): Option[Throwable] = t match {
      case e: VirtualMachineError => None
      case e: LinkageError => None
      case e: IsolateError => None
      case _ => Some(t)
    }
  }

  val ignoreNonLethal: PartialFunction[Throwable, Unit] = {
    case t if isNonLethal(t) => // ignore
  }

  /** Any object that contains a unique id within some scope.
   */
  trait Identifiable {
    def uid: Long
  }

  /* extensions on tuples */

  class Tuple2Extensions[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val tuple: (Signal[T], Signal[S])) {
    def mutate[M <: ReactMutable](mutable: M)(f: (T, S) => Unit) = {
      val s = new Tuple2Extensions.Mutate(tuple, mutable, f)
      s.subscription = Events.CompositeSubscription(
        tuple._1.observe(s.m1),
        tuple._2.observe(s.m2)
      )
      s
    }
  }

  implicit def tuple2ext[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (tuple: (Signal[T], Signal[S])) = new Tuple2Extensions[T, S](tuple)

  object Tuple2Extensions {
    class Mutate[
      @spec(Int, Long, Double) T,
      @spec(Int, Long, Double) S,
      M <: ReactMutable
    ](
      val tuple: (Signal[T], Signal[S]),
      val mutable: M,
      val f: (T, S) => Unit
    ) extends Events.ProxySubscription {
      val m1 = new Reactor[T] {
        def react(value: T) {
          try f(tuple._1(), tuple._2())
          catch {
            case t if isNonLethal(t) => except(t)
          }
          mutable.mutation()
        }
        def except(t: Throwable) {
          mutable.exception(t)
        }
        def unreact() {
        }
      }
      val m2 = new Reactor[S] {
        def react(value: S) {
          try f(tuple._1(), tuple._2())
          catch {
            case t if isNonLethal(t) => except(t)
          }
          mutable.mutation()
        }
        def except(t: Throwable) {
          mutable.exception(t)
        }
        def unreact() {
        }
      }
      var subscription = Events.Subscription.empty
    }
  }

  class Tuple3Extensions[
    @spec(Int, Long, Double) T,
    @spec(Int, Long, Double) S,
    @spec(Int, Long, Double) U
  ](val tuple: (Signal[T], Signal[S], Signal[U])) {
    def mutate[M <: ReactMutable](mutable: M)(f: (T, S, U) => Unit) = {
      val s = new Tuple3Extensions.Mutate(tuple, mutable, f)
      s.subscription = Events.CompositeSubscription(
        tuple._1.observe(s.m1),
        tuple._2.observe(s.m2),
        tuple._3.observe(s.m3)
      )
      s
    }
  }

  implicit def tuple3ext[
    @spec(Int, Long, Double) T,
    @spec(Int, Long, Double) S,
    @spec(Int, Long, Double) U
  ](tuple: (Signal[T], Signal[S], Signal[U])) = new Tuple3Extensions[T, S, U](tuple)

  object Tuple3Extensions {
    class Mutate[
      @spec(Int, Long, Double) T,
      @spec(Int, Long, Double) S,
      @spec(Int, Long, Double) U,
      M <: ReactMutable
    ](
      val tuple: (Signal[T], Signal[S], Signal[U]),
      val mutable: M,
      val f: (T, S, U) => Unit
    ) extends Events.ProxySubscription {
      val m1 = new Reactor[T] {
        def react(value: T) {
          try f(tuple._1(), tuple._2(), tuple._3())
          catch {
            case t if isNonLethal(t) => except(t)
          }
          mutable.mutation()
        }
        def except(t: Throwable) {
          mutable.exception(t)
        }
        def unreact() {
        }
      }
      val m2 = new Reactor[S] {
        def react(value: S) {
          try f(tuple._1(), tuple._2(), tuple._3())
          catch {
            case t if isNonLethal(t) => except(t)
          }
          mutable.mutation()
        }
        def except(t: Throwable) {
          mutable.exception(t)
        }
        def unreact() {
        }
      }
      val m3 = new Reactor[U] {
        def react(value: U) {
          try f(tuple._1(), tuple._2(), tuple._3())
          catch {
            case t if isNonLethal(t) => except(t)
          }
          mutable.mutation()
        }
        def except(t: Throwable) {
          mutable.exception(t)
        }
        def unreact() {
        }
      }
      var subscription = Events.Subscription.empty
    }
  }

  implicit class OptionOps[T](val opt: Option[T]) extends AnyVal {
    def react: Ivar[T] = opt match {
      case Some(v) => Ivar(v)
      case None => Ivar.unreacted[T]
    }
  }

  /* system events */

  /** System events are a special kind of internal events that can be observed
   *  by isolates.
   */
  sealed trait SysEvent

  /** Denotes start of an isolate.
   *
   *  Produced before any other event.
   */
  case object IsoStarted extends SysEvent

  /** Denotes the termination of an isolate.
   *
   *  Called after all other events.
   */
  case object IsoTerminated extends SysEvent

  /** Denotes that the isolate was scheduled for execution by the scheduler.
   *
   *  Always sent after `IsoStarted` and before `IsoTerminated`.
   *
   *  This event usually occurs when isolate is woken up to process incoming events,
   *  but may be invoked even if there are no pending events.
   *  This event is typically used in conjunction with a scheduler that periodically
   *  wakes up the isolate.
   */
  case object IsoScheduled extends SysEvent

  /** Denotes that the isolate was preempted by the scheduler.
   *
   *  Always sent after `IsoStarted` and before `IsoTerminated`.
   *
   *  When the isolate is preempted, it loses control of the execution thread, until the
   *  scheduler schedules it again on some (possibly the same) thread.
   *  This event is typically used to send another message back to the isolate,
   *  indicating that he should be scheduled again later.
   */
  case object IsoPreempted extends SysEvent

  /** Denotes that the isolate died due to an exception.
   *
   *  This event is sent after `IsoStarted`.
   *  This event is sent before `IsoTerminated`, *unless* the exception is thrown while
   *  `IsoTerminated` is being processed, in which case the `IsoDied` is not sent.
   *
   *  Note that, if the exception is thrown during the isolate constructor invocation
   *  and before the appropriate event handler is created, this event cannot be sent
   *  to that event handler.
   *
   *  @param t              the exception that the isolate threw
   */
  case class IsoDied(t: Throwable) extends SysEvent

  /* exceptions */

  object exception {
    def apply(obj: Any) = throw new RuntimeException(obj.toString)
    def illegalArg(msg: String) = throw new IllegalArgumentException(msg)
    def illegalState(obj: Any) = throw new IllegalStateException(obj.toString)
  }

}


