package org.reactress






/** A special type of a reactive value that caches the last emitted event.
 *
 *  This last event is called the signal's ''value''.
 *  It can be read using the `Signal`'s `apply` method.
 *  
 *  @tparam T        the type of the events in this signal
 */
trait Signal[@spec(Int, Long, Double) +T]
extends Reactive[T] {
  self =>

  /** Returns the last event produced by `this` signal.
   *
   *  @return         the signal's value
   */
  def apply(): T

  /** Maps the signal using the specified mapping function `f`.
   *
   *  @tparam S       type of the mapped signal
   *  @param f        mapping function for the events in `this` signal
   *  @return         a subscription and a signal with the mapped events
   */
  override def map[@spec(Int, Long, Double) S](f: T => S): Signal[S] with Reactive.Subscription = {
    val sm = new Signal.Map(self, f)
    sm.subscription = self onReaction sm
    sm
  }

  /** A renewed instance of this signal emitting the same events,
   *  but having a different set of subscribers.
   *
   *  @return         a subscription and a new instance of `this` signal
   */
  def renewed: Signal[T] with Reactive.Subscription = this.signal(apply())

  def changes: Signal[T] with Reactive.Subscription = {
    val initial = this()
    val sc = new Signal.Changes(self, initial)
    sc.subscription = self onReaction sc
    sc
  }

  def diffPast[@spec(Int, Long, Double) S](z: S)(op: (T, T) => S): Signal[S] with Reactive.Subscription = {
    val initial = this()
    val sd = new Signal.DiffPast(self, initial, z, op)
    sd.subscription = self onReaction sd
    sd
  }

  def zip[@spec(Int, Long, Double) S, @spec(Int, Long, Double) R](that: Signal[S])(f: (T, S) => R): Signal[R] with Reactive.Subscription = {
    val sz = new Signal.Zip(self, that, f)
    sz.subscription = Reactive.CompositeSubscription(
      self onReaction sz.selfReactor,
      that onReaction sz.thatReactor
    )
    sz
  }

  /* higher order */

  def muxSignal[@spec(Int, Long, Double) S]()(implicit evidence: T <:< Signal[S]): Signal[S] = {
    new Signal.Mux[T, S](this, evidence)
  }

}


object Signal {

  implicit class SignalOps[@spec(Int, Long, Double) T](val self: Signal[T]) {
    def scanPastNow(op: (T, T) => T): Signal[T] with Reactive.Subscription = {
      val initial = self()
      val srp = new Signal.ScanPastNow(self, initial, op)
      srp.subscription = self onReaction srp
      srp
    }

    /** Creates a new signal that emits tuples of the current
     *  and the last event emitted by `this` signal.
     *
     *  {{{
     *  time  ---------------------->
     *  this  1----2------3----4---->
     *  past2 i,1--1,2----2,3--3,4-->
     *  }}}
     *
     *  @param init     the initial previous value, `i` in the diagram above
     *  @return         a subscription and a signal of tuples of the current and last event
     */
    def past2(init: T) = self.scanPast((init, self())) {
      (t, x) => (t._2, x)
    }
  }

  class Map[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Signal[T], val f: T => S)
  extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    private var cached = f(self.apply)
    def apply() = cached
    def react(value: T) {
      cached = f(value)
      reactAll(cached)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Reactive.Subscription.empty
  }

  class ScanPastNow[@spec(Int, Long, Double) T]
    (val self: Signal[T], initial: T, op: (T, T) => T)
  extends Signal.Default[T] with Reactor[T] with Reactive.ProxySubscription {
    private var cached = initial
    def apply() = cached
    def react(value: T) {
      cached = op(cached, value)
      reactAll(cached)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Reactive.Subscription.empty
  }

  class Changes[@spec(Int, Long, Double) T]
    (val self: Signal[T], var cached: T)
  extends Signal.Default[T] with Reactor[T] with Reactive.ProxySubscription {
    def apply() = cached
    def react(value: T) {
      if (cached != value) {
        cached = value
        reactAll(cached)
      }
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Reactive.Subscription.empty
  }

  class DiffPast[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Signal[T], var last: T, var cached: S, val op: (T, T) => S)
  extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    def apply() = cached
    def react(value: T) {
      cached = op(value, last)
      last = value
      reactAll(cached)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Reactive.Subscription.empty
  }

  class Zip[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, @spec(Int, Long, Double) R]
    (val self: Signal[T], val that: Signal[S], val f: (T, S) => R)
  extends Signal.Default[R] with Reactive.ProxySubscription {
    zipped =>
    private[reactress] var cached = f(self(), that())
    private[reactress] var left = 2
    private[reactress] def unreact() {
      left -= 1
      if (left == 0) zipped.unreactAll()
    }
    private[reactress] val selfReactor = new Reactor[T] {
      def react(value: T) {
        cached = f(value, that())
        zipped.reactAll(cached)
      }
      def unreact() = zipped.unreact()
    }
    private[reactress] val thatReactor = new Reactor[S] {
      def react(value: S) {
        cached = f(self(), value)
        zipped.reactAll(cached)
      }
      def unreact() = zipped.unreact()
    }
    def apply() = cached
    var subscription = Reactive.Subscription.empty
  }

  trait Default[@spec(Int, Long, Double) T] extends Signal[T] with Reactive.Default[T]

  class Constant[@spec(Int, Long, Double) T](private val value: T)
  extends Signal[T] with Reactive.Never[T] {
    def apply() = value
  }

  def Constant[@spec(Int, Long, Double) T](value: T) = new Constant(value)

  trait Proxy[@spec(Int, Long, Double) T]
  extends Signal[T] {
    def proxy: Signal[T]
    def apply() = proxy()
    def hasSubscriptions = proxy.hasSubscriptions
    def onReaction(r: Reactor[T]) = proxy.onReaction(r)
  }

  final class Mutable[T <: AnyRef](private val m: T)
  extends Signal.Default[T] with ReactMutable.Subscriptions {
    def apply() = m
    override def onMutated() = reactAll(m)
  }

  def Mutable[T <: AnyRef](v: T) = new Mutable[T](v)

  class Aggregate[@spec(Int, Long, Double) T]
    (private val root: Signal[T] with Reactive.Subscription, private val subscriptions: Seq[Reactive.Subscription])
  extends Signal.Default[T] with Reactive.Subscription {
    def apply() = root()
    def unsubscribe() {
      for (s <- subscriptions) s.unsubscribe()
    }
  }

  def Aggregate[@spec(Int, Long, Double) T](signals: Signal[T]*)(op: (T, T) => T) = {
    require(signals.length > 0)
    val leaves = signals.map(_.renewed)
    var ss = leaves
    while (ss.length != 1) {
      val nextLevel = for (pair <- ss.grouped(2)) yield pair match {
        case Seq(s1, s2) => (s1 zip s2) { (x, y) => op(x, y) }
        case Seq(s) => s
      }
      ss = nextLevel.toBuffer
    }
    val root = ss(0)
    new Aggregate[T](root, leaves)
  }

  class Mux[T, @spec(Int, Long, Double) S]
    (val self: Signal[T], val evidence: T <:< Signal[S])
  extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    muxed =>
    import Reactive.Subscription
    private var value: S = _
    private[reactress] var currentSubscription: Subscription = null
    private[reactress] var terminated = false
    def apply() = value
    def newReactor: Reactor[S] = new Reactor[S] {
      def react(v: S) = {
        value = v
        reactAll(value)
      }
      def unreact() {
        currentSubscription = Subscription.empty
        checkUnreact()
      }
    }
    def checkUnreact() = if (terminated && currentSubscription == Subscription.empty) unreactAll()
    def react(v: T) {
      val nextSignal = evidence(v)
      currentSubscription.unsubscribe()
      value = nextSignal()
      currentSubscription = nextSignal onReaction newReactor
      reactAll(value)
    }
    def unreact() {
      terminated = true
      checkUnreact()
    }
    override def unsubscribe() {
      currentSubscription.unsubscribe()
      currentSubscription = Subscription.empty
      super.unsubscribe()
    }
    var subscription: Subscription = null
    def init(e: T <:< Reactive[S]) {
      value = evidence(self()).apply()
      currentSubscription = Subscription.empty
      subscription = self onReaction this
    }
    init(evidence)
  }

}