package scala.reactive






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
   *  {{{
   *  time ---------------->
   *  this --1---2----3--4->
   *  map  --2---4----6--8->
   *  }}} 
   *
   *  @tparam S       type of the mapped signal
   *  @param f        mapping function for the events in `this` signal
   *  @return         a subscription and a signal with the mapped events
   */
  override def map[@spec(Int, Long, Double) S](f: T => S): Signal[S] with Reactive.Subscription = {
    val sm = new Signal.Map(self, f)
    sm.subscription = self observe sm
    sm
  }

  /** A renewed instance of this signal emitting the same events,
   *  but having a different set of subscribers.
   *
   *  {{{
   *  time    ------------->
   *  this    --1----2--3-->
   *  renewed --1----2--3-->
   *  }}}
   *
   *  @return         a subscription and a new instance of `this` signal
   */
  def renewed: Signal[T] with Reactive.Subscription = this.signal(apply())

  /** A signal that only emits events when the value of `this` signal changes.
   *
   *  {{{
   *  time    --------------->
   *  this    --1---2--2--3-->
   *  changes --1---2-----3-->
   *  }}}
   *
   *  @return         a subscription and the signal with changes of `this`
   */
  def changes: Signal[T] with Reactive.Subscription = {
    val initial = this()
    val sc = new Signal.Changes(self, initial)
    sc.subscription = self observe sc
    sc
  }

  /** A signal that produces difference events between the current and previous value of `this` signal.
   *
   *  {{{
   *  time ---------------->
   *  this --1--3---6---7-->
   *  diff --z--2---3---1-->
   *  }}}
   *  
   *  @tparam S       the type of the difference event
   *  @param z        the initial value for the difference
   *  @param op       the operator that computes the difference between consecutive events
   *  @return         a subscription and a signal with the difference value
   */
  def diffPast[@spec(Int, Long, Double) S](z: S)(op: (T, T) => S): Signal[S] with Reactive.Subscription = {
    val initial = this()
    val sd = new Signal.DiffPast(self, initial, z, op)
    sd.subscription = self observe sd
    sd
  }

  /** Zips values of `this` and `that` signal using the specified function `f`.
   *
   *  Whenever either of the two signals change the resulting signal also changes.
   *  When `this` emits an event, the current value of `that` is used to produce a signal on `that`,
   *  and vice versa.
   *
   *  {{{
   *  time --------------------------------->
   *  this --1----2-----4----------8-------->
   *  that --a----------------b---------c--->
   *  zip  --1,a--2,a---4,a---4,b--8,b--8,c->
   *  }}}
   *
   *  The resulting tuple of events from `this` and `that` is mapped using the
   *  user-specified mapping function `f`.
   *  For example, to produce tuples:
   *
   *  {{{
   *  val tuples = (a zip b) { (a, b) => (a, b) }
   *  }}}
   *
   *  To produce the difference between two integer signals:
   *
   *  {{{
   *  val differences = (a zip b)(_ - _)
   *  }}}
   *
   *  '''Note:''': clients looking into pairing incoming events from two signals
   *  you should use the `sync` method inherited from `Reactive`.
   *
   *  @tparam S        the type of `that` signal
   *  @tparam R        the type of the resulting signal
   *  @param that      the signal to zip `this` with
   *  @param f         the function that maps a tuple of values into an outgoing event
   *  @return          a subscription and the reactive that emits zipped events
   */
  def zip[@spec(Int, Long, Double) S, @spec(Int, Long, Double) R](that: Signal[S])(f: (T, S) => R): Signal[R] with Reactive.Subscription = {
    val sz = new Signal.Zip(self, that, f)
    sz.subscription = Reactive.CompositeSubscription(
      self observe sz.selfReactor,
      that observe sz.thatReactor
    )
    sz
  }

  /* higher order */

  /** Creates a signal that uses the current signal nested in `this` signal to compute the resulting value,
   *  in effect multiplexing the nested signals.
   *
   *  Whenever the nested signal changes, or the value of the nested signal changes,
   *  an event with the current nested signal value is emitted
   *  and stored as the value of the resulting signal.
   *
   *  Unreacts when both `this` and the last nested signal unreact.
   *
   *  {{{
   *  time      -------------------------------->
   *  this      1--2--3----4--5--6-------------->
   *                     0--0--0-----0---0--0--->
   *                               1--2---4--8-->
   *  muxSignal 1--2--3--0--0--0---1--2---4--8-->
   *  }}}
   *  
   *  This is similar to `mux`, but emits the initial value of the signal as an event too --
   *  this is because `mux` does not require the nested reactive to be a signal.
   *
   *  '''Use case:'''
   *
   *  {{{
   *  def muxSignal[S](): Signal[S]
   *  }}}
   *
   *  @tparam S         type of the nested signal
   *  @param evidence   evidence that the type of `this` signal `T` is a signal of type `S`
   *  @return           a subscription and a signal with the multiplexed values.
   */
  def muxSignal[@spec(Int, Long, Double) S]()(implicit evidence: T <:< Signal[S]): Signal[S] with Reactive.Subscription = {
    new Signal.Mux[T, S](this, evidence)
  }

}


object Signal {

  implicit class SignalOps[@spec(Int, Long, Double) T](val self: Signal[T]) {
    /** Scans the events in the past of `this` signal starting from the
     *  current value of `this` signal.
     *
     *  {{{
     *  time        -------------------->
     *  this        1--2----4-----8----->
     *  scanPastNow 1--3----7-----15---->
     *  }}}
     */
    def scanPastNow(op: (T, T) => T): Signal[T] with Reactive.Subscription = {
      val initial = self()
      val srp = new Signal.ScanPastNow(self, initial, op)
      srp.subscription = self observe srp
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

  private[reactive] class Map[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
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

  private[reactive] class ScanPastNow[@spec(Int, Long, Double) T]
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

  private[reactive] class Changes[@spec(Int, Long, Double) T]
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

  private[reactive] class DiffPast[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
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

  private[reactive] class Zip[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, @spec(Int, Long, Double) R]
    (val self: Signal[T], val that: Signal[S], val f: (T, S) => R)
  extends Signal.Default[R] with Reactive.ProxySubscription {
    zipped =>
    private[reactive] var cached = f(self(), that())
    private[reactive] var left = 2
    private[reactive] def unreact() {
      left -= 1
      if (left == 0) zipped.unreactAll()
    }
    private[reactive] val selfReactor = new Reactor[T] {
      def react(value: T) {
        cached = f(value, that())
        zipped.reactAll(cached)
      }
      def unreact() = zipped.unreact()
    }
    private[reactive] val thatReactor = new Reactor[S] {
      def react(value: S) {
        cached = f(self(), value)
        zipped.reactAll(cached)
      }
      def unreact() = zipped.unreact()
    }
    def apply() = cached
    var subscription = Reactive.Subscription.empty
  }

  /** The default implementation of the signal.
   *
   *  Keeps a list of weak references to dependent signals.
   *
   *  @tparam T         the type of this signal
   */
  trait Default[@spec(Int, Long, Double) T] extends Signal[T] with Reactive.Default[T]

  private[reactive] class Constant[@spec(Int, Long, Double) T](private val value: T)
  extends Signal[T] with Reactive.Never[T] {
    def apply() = value
  }

  /** A signal that never changes its value.
   *
   *  @tparam T         the type of this signal
   *  @param value      the constant value of this signal
   *  @return           the resulting constant signal
   */
  def Constant[@spec(Int, Long, Double) T](value: T) = new Constant(value)

  /** A proxy that emits events from the underlying signal
   *  and has the same value as the underlying signal.
   *
   *  @tparam T         the type of the proxy signal
   */
  trait Proxy[@spec(Int, Long, Double) T]
  extends Signal[T] {
    def proxy: Signal[T]
    def apply() = proxy()
    def hasSubscriptions = proxy.hasSubscriptions
    def observe(r: Reactor[T]) = proxy.observe(r)
  }

  /** A signal that emits events that are mutable values.
   *
   *  An event with underlying mutable value `m` is emitted
   *  whenever the mutable value was potentially mutated.
   *  This type of a signal provides a controlled way of
   *  manipulating mutable values.
   *
   *  '''Note:'''
   *  The underlying mutable value `m` must '''never''' be
   *  mutated directly by accessing the value of the signal
   *  and changing the mutable value `m`.
   *  Instead, the `mutate` operation on `Reactive`s should
   *  be used to mutate `m`.
   *
   *  Example:
   *
   *  {{{
   *  val systemMessages = new Reactive.Emitter[String]
   *  val log = new Signal.Mutable(new mutable.ArrayBuffer[String])
   *  val logMutations = systemMessages.mutate(log) { msg =>
   *    log() += msg
   *  }
   *  systemMessages += "New message arrived!" // log() now contains the message
   *  }}}
   *
   *  The `mutate` combinator ensures that the value is never mutated concurrently
   *  by different threads.
   *  In fact, as long as there are no feedback loops in the dataflow graph,
   *  the same thread will never modify the mutable signal at the same time.
   *  See the `mutate` method on `Reactive`s for more information.
   *
   *  @see [[scala.reactive.Reactive]]
   *  @tparam T          the type of the underlying mutable object
   *  @param m           the mutable object
   */
  final class Mutable[T <: AnyRef](private val m: T)
  extends Signal.Default[T] with ReactMutable.Subscriptions {
    def apply() = m
    override def onMutated() = reactAll(m)
  }

  /** Creates a mutable signal.
   *
   *  @see [[scala.reactive.Signal.Mutable]]
   *
   *  @tparam T          the type of the underlying mutable object
   *  @param v           the mutable object
   */
  def Mutable[T <: AnyRef](v: T) = new Mutable[T](v)

  private[reactive] class Aggregate[@spec(Int, Long, Double) T]
    (private val root: Signal[T] with Reactive.Subscription, private val subscriptions: Seq[Reactive.Subscription])
  extends Signal.Default[T] with Reactive.Subscription {
    def apply() = root()
    def unsubscribe() {
      for (s <- subscriptions) s.unsubscribe()
    }
  }

  /** A signal that is the aggregation of the values of other `signals`.
   *
   *  At any point during execution this signal will contain
   *  an event obtained by applying `op` on the values of all
   *  the events in `signals`.
   *  This signal aggregate is called a static aggregate
   *  since the `signals` set is specified during aggregate
   *  creation and cannot be changed afterwards.
   *
   *  The signal aggregate creates an aggregation tree data structure,
   *  so a value update in one of the `signals` requires only O(log n)
   *  steps to update the value of the aggregate signal.
   *
   *  Example:
   *  {{{
   *  val emitters = for (0 until 10) yield new Reactive.Emitter[Int]
   *  val ag = Signal.Aggregate(emitters)(_ + _)
   *  }}}
   *
   *  The aggregation operator needs to be associative,
   *  but does not need to be commutative.
   *  For example, string concatenation for signals of strings
   *  or addition for integer signals is perfectly fine.
   *  Subtraction for integer signals, for example,
   *  is not associative and not allowed.
   *
   *  @tparam T          type of the aggregate signal
   *  @param signals     signals for the aggregation
   *  @param op          the aggregation operator, must be associative
   */
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

  private[reactive] class Mux[T, @spec(Int, Long, Double) S]
    (val self: Signal[T], val evidence: T <:< Signal[S])
  extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    muxed =>
    import Reactive.Subscription
    private var value: S = _
    private[reactive] var currentSubscription: Subscription = null
    private[reactive] var terminated = false
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
      currentSubscription = nextSignal observe newReactor
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
      subscription = self observe this
    }
    init(evidence)
  }

}