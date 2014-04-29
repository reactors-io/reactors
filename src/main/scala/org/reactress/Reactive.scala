package org.reactress



import scala.annotation.tailrec
import scala.collection._
import util._



/** A basic reactive value.
 *
 *  Reactive values, or simply, ''reactives'' are special objects that may produce events
 *  of a certain type `T`.
 *  Clients may subscribe side-effecting functions (i.e. callbacks)
 *  to these events with `onReaction`, `onEvent`, `onCase` and `on` --
 *  each of these methods will invoke the callback when an event
 *  is produced, but some may be more suitable depending on the use-case.
 *
 *  A reactive produces events until it ''unreacts''.
 *  After the reactive value unreacts, it never produces an event again.
 *  
 *  Reactive values can also be manipulated using declarative combinators
 *  such as `map`, `filter`, `until`, `after` and `scanPast`:
 *
 *  {{{
 *  def positiveSquares(r: Reactive[Int]) = r.map(x => x * x).filter(_ != 0)
 *  }}}
 *
 *  With the exception of `onX` family of methods,
 *  operators passed to these declarative combinators should be pure --
 *  they should not have any side-effects.
 *
 *  The result of applying a declarative combinator on `Reactive[T]` is usually another
 *  `Reactive[S]`, possibly with a different type parameter.
 *  Most declarative combinators return a `Subscription` object used to
 *  `unsubscribe` from their event source.
 *  This is not necessary in most situations,
 *  but can be used to prune the dataflow graph.
 *
 *  Reactive values are specialized for `Int`, `Long` and `Double`.
 *
 *  Every reactive value is bound to a specific `Isolate`.
 *  A reactive value will only produce events during the execution of that isolate --
 *  events are never triggered on a different isolate.
 *  It is forbidden to share reactive values between isolates --
 *  instead, an isolate should be attached to a specific channel.
 *
 *  Every reactive value is either ''active'' or ''passive''.
 *  Active reactive values produce events irregardless if there are any subscribers
 *  to these events.
 *  Examples are mouse movement, file changes or operating system events.
 *  Here is an active reactive:
 *
 *  {{{
 *  val emitter = new Reactive.Emitter[Int]
 *  emitter += 1 // events are produced regardless
 *               // of subscribers like evens below
 *  val evens = emitter.filter(_ % 2 == 0)
 *  }}}
 *
 *  Passive reactive values produce events depending on whether there are any
 *  subscribers -- typically, each subscriber gets a sequence of events
 *  upon subscription.
 *
 *  {{{
 *  val items = Reactive.items(Array(1, 2, 3))
 *  items.onEvent(println) // array elements are emitted on-demand
 *  items.onEvent(println) // twice -- once for each `onEvent` call
 *  }}}
 *
 *  @author        Aleksandar Prokopec
 *
 *  @tparam T      type of the events in this reactive value
 */
trait Reactive[@spec(Int, Long, Double) +T] {
  self =>

  /** Is there any other reactive that depends on the events
   *  produced by this reactive.
   *
   *  Passive reactives, such as `Reactive.items` will always returns `false`.
   *  Other reactives will return `true` if there are any subscribers attached to them.
   *  This method is used internally to optimize and recycle some subscriptions away.
   */
  private[reactress] def hasSubscriptions: Boolean

  /** Attaches a new `reactor` to this reactive
   *  that is called multiple times when an event is produced
   *  and once when the reactive is terminated.
   *
   *  Reactives can create events specifically for this reactor,
   *  in which case they are called ''passive''.
   *  A passive reactive can create events both synchronously and asynchronously,
   *  but it will only do so on its own isolate.
   *
   *  An ''active'' reactive value will produce events irregardless of the reactors subscribed to it.
   *  Subscribing to an active reactive value only forwards those events that have
   *  been produced after the subscription started.
   *
   *  @param reactor     the reactor that accepts `react` and `unreact` events
   *  @return            a subscription for unsubscribing from reactions
   */
  def onReaction(reactor: Reactor[T]): Reactive.Subscription

  /** A shorthand for `onReaction` -- the specified function is invoked whenever there is an event.
   *
   *  @param reactor     the callback for events
   *  @return            a subcriptions for unsubscribing from reactions
   */
  def onEvent(reactor: T => Unit): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(event: T) = reactor(event)
    def unreact() {}
  })

  /** A shorthand for `onReaction` -- the specified partial function is applied to only those events
   *  for which is defined.
   *  
   *  This method only works for `AnyRef` values.
   *
   *  Example: 
   *
   *  {{{
   *  r onCase {
   *    case s: String => println(s)
   *    case n: Int    => println("number " + s)
   *  }
   *  }}}
   *
   *  '''Use case''':
   *
   *  {{{
   *  def onCase(reactor: PartialFunction[T, Unit]): Reactive.Subscription
   *  }}}
   *  
   *  @param reactor     the callback for those events for which it is defined
   *  @return            a subscription for unsubscribing from reactions
   */
  def onCase(reactor: PartialFunction[T, Unit])(implicit sub: T <:< AnyRef): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(event: T) = if (reactor.isDefinedAt(event)) reactor(event)
    def unreact() {}
  })

  /** A shorthand for `onReaction` -- called whenever an event occurs.
   *
   *  This method is handy when the precise event is not important,
   *  or the type of the event is `Unit`.
   *
   *  @param reactor     the callback invoked when an event arrives
   *  @return            a subscription for unsubscribing from reactions
   */
  def on(reactor: =>Unit): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(value: T) = reactor
    def unreact() {}
  })

  /** Executes the specified block when `this` reactive unreacts.
   *
   *  @param f           the callback invoked when `this` unreacts
   *  @return            a subscription for the unreaction notification
   */
  def onUnreact(reactor: =>Unit): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(value: T) {}
    def unreact() = reactor
  })

  /** Executes the specified function every time an event arrives.
   *
   *  Semantically equivalent to `onEvent`,
   *  but supports `for`-loop syntax with reactive values.
   *
   *  {{{
   *  for (event <- r) println("Event arrived: " + event)
   *  }}}
   *
   *  @param f           the callback invoked when an event arrives
   *  @return            a subscription that is also a reactive value
   *                     producing `Unit` events after each callback invocation
   */
  def foreach(f: T => Unit): Reactive[Unit] with Reactive.Subscription = {
    val rf = new Reactive.Foreach(self, f)
    rf.subscription = self onReaction rf
    rf
  }

  /** Creates a new reactive `s` that produces events by consecutively
   *  applying the specified operator `op` to the previous event that `s`
   *  produced and the current event that this reactive value produced.
   *
   *  The `scanPast` operation allows the current event from this reactive to be mapped into a different
   *  event by looking "into the past", i.e. at the event previously emitted by the resulting reactive.
   *
   *  Example -- assume that a reactive value `r` produces events `1`, `2` and `3`.
   *  The following `s`:
   *
   *  {{{
   *  val s = r.scanPast(0)((sum, n) => sum + n)
   *  }}}
   *
   *  will produce events `1`, `3` (`1 + 2`) and `6` (`3 + 3`).
   *  '''Note:''' the initial value `0` is '''not emitted'''.
   *  
   *  The `scanPast` can also be used to produce a reactive value of a different type:
   *  The following produces a complete history of all the events seen so far:
   *
   *  {{{
   *  val s2 = r.scanPast(List[Int]()) {
   *    (history, n) => n :: history
   *  }
   *  }}}
   *  
   *  The `s2` will produce events `1 :: Nil`, `2 :: 1 :: Nil` and `3 :: 2 :: 1 :: Nil`.
   *  '''Note:''' the initial value `Nil` is '''not emitted'''.
   *
   *  The resulting reactive value is not only a reactive value, but also a `Signal`,
   *  so the value of the previous event can be obtained by calling `apply` at any time.
   *
   *  This operation is closely related to a `scanLeft` on a collection --
   *  if a reactive value were a sequence of elements, then `scanLeft` would produce
   *  a new sequence whose elements correspond to the events of the resulting reactive.
   *
   *  @tparam S        the type of the events in the resulting reactive value
   *  @param z         the initial value of the scan past
   *  @param op        the operator the combines the last produced and the current event into a new one
   *  @return          a subscription that is also a reactive value that scans events from `this` reactive value
   */
  def scanPast[@spec(Int, Long, Double) S](z: S)(op: (S, T) => S): Signal[S] with Reactive.Subscription = {
    val r = new Reactive.ScanPast(self, z, op)
    r.subscription = self onReaction r
    r
  }

  /** Checks if this reactive value is also a signal.
   *
   *  @return         `true` if the reactive value is a signal, `false` otherwise
   */
  def isSignal: Boolean = this match {
    case s: Signal[T] => true
    case _ => false
  }

  /** Mutates the target reactive mutable called `mutable` each time `this` reactive value produces an event.
   *
   *  One type of a reactive mutable is a mutable signal (`Signal.Mutable`),
   *  which is a wrapper for regular mutable objects.
   *  Here is an example, given a reactive of type `r`:
   *
   *  {{{
   *  val eventLog = Signal.Mutable(mutable.Buffer[String]())
   *  val eventLogMutations = r.mutate(eventLog) { event =>
   *    eventLog() += "at " + System.nanoTime + ": " + event
   *  } // <-- eventLog event propagated
   *  }}}
   *
   *  Whenever an event arrives on `r`, an entry is added to the buffer underlying `eventLog`.
   *  After the `mutation` completes, a modification event is produced by the `eventLog`
   *  and can be used subsequently:
   *
   *  {{{
   *  val uiUpdates = eventLog onEvent { b =>
   *    eventListWidget.add(b.last)
   *  }
   *  }}}
   *
   *  '''Use case:'''
   *
   *  {{{
   *  def mutate(mutable: ReactMutable)(mutation: T => Unit): Reactive.Subscription
   *  }}}
   *
   *  @note No two events will ever be concurrently processed by different threads on the same reactive mutable,
   *  but an event that is propagated from within the `mutation` can trigger an event on `this`.
   *  The result is that `mutation` is invoked concurrently on the same thread.
   *  The following code is problematic has a feedback loop in the dataflow graph:
   *  
   *  {{{
   *  val emitter = new Reactive.Emitter[Int]
   *  val cell = ReactCell(0) // type of ReactMutable
   *  emitter.mutate(cell) { n =>
   *    cell := n
   *    if (n == 0)
   *      emitter += n + 1 // <-- event propagated
   *    assert(cell() == n)
   *  }
   *  emitter += 0
   *  }}}
   *  
   *  The statement `emitter += n + 1` in the `mutate` block
   *  suspends the current mutation, calls the mutation
   *  recursively and changes the value of `cell`, and the assertion fails when
   *  the first mutation resumes.
   *
   *  Care must be taken to avoid `mutation` from emitting events that have feedback loops.
   *
   *  @tparam M         the type of the reactive mutable value
   *  @param mutable    the target mutable to be mutated with events from this stream
   *  @param mutation   the function that modifies `mutable` given an event of type `T`
   *  @return           a subscription used to cancel this mutation
   */
  def mutate[M <: ReactMutable](mutable: M)(mutation: T => Unit): Reactive.Subscription = {
    val rm = new Reactive.Mutate(self, mutable, mutation)
    rm.subscription = mutable.bindSubscription(self onReaction rm)
    rm
  }

  private def mutablesCompositeSubscription[M <: ReactMutable](mutables: Seq[M], selfsub: Reactive.Subscription) = {
    for (m <- mutables) yield m.bindSubscription(selfsub)
  }

  /** Mutates multiple reactive mutables `m1`, `m2` and `mr` each time
   *  `this` reactive value produces an event.
   *  
   *  This version of the `mutate` works on multiple reactive values.
   *  
   *  @tparam M          the type of the reactive mutable value
   *  @param m1          the first mutable
   *  @param m2          the second mutable
   *  @param mr          the rest of the mutables
   *  @param mutation    the function that modifies the mutables
   *  @return            a subscription used to cancel this mutation
   */
  def mutate[M <: ReactMutable](m1: M, m2: M, mr: M*)(mutation: T => Unit): Reactive.Subscription = {
    val mutables = Seq(m1, m2) ++ mr
    val rm = new Reactive.MutateMany(self, mutables, mutation)
    val selfsub = self onReaction rm
    val subs = mutablesCompositeSubscription(mutables, selfsub)
    rm.subscription = Reactive.CompositeSubscription(subs: _*)
    rm
  }

  /** Creates a new reactive value that produces events from `this` reactive value
   *  only after `that` produces an event.
   *
   *  After `that` emits some event, all events from `this` are produced on the resulting reactive.
   *  If `that` unreacts before an event is produced on `this`, the resulting reactive unreacts.
   *  If `this` unreacts, the resulting reactive unreacts.
   *
   *  @tparam S          the type of `that` reactive
   *  @param that        the reactive after whose first event the result can start propagating events
   *  @return            a subscription and the resulting reactive that emits only after `that` emits
   *                     at least once.
   */
  def after[@spec(Int, Long, Double) S](that: Reactive[S]): Reactive[T] with Reactive.Subscription = {
    val ra = new Reactive.After(self, that)
    ra.selfSubscription = self onReaction ra.selfReactor
    ra.thatSubscription = that onReaction ra.thatReactor
    ra.subscription = Reactive.CompositeSubscription(ra.selfSubscription, ra.thatSubscription)
    ra
  }

  /** Creates a new reactive value that produces events from `this` reactive value
   *  until `that` produces an event.
   *  
   *  If `this` unreacts before `that` produces a value, the resulting reactive unreacts.
   *  Otherwise, the resulting reactive unreacts whenever `that` produces a value.
   *
   *  @tparam S         the type of `that` reactive
   *  @param that       the reactive until whose first event the result propagates events
   *  @return           a subscription and the resulting reactive that emits only until `that` emits
   */
  def until[@spec(Int, Long, Double) S](that: Reactive[S]): Reactive[T] with Reactive.Subscription = {
    val ru = new Reactive.Until(self, that)
    ru.selfSubscription = self onReaction ru.selfReactor
    ru.thatSubscription = that onReaction ru.thatReactor
    ru.subscription = Reactive.CompositeSubscription(ru.selfSubscription, ru.thatSubscription)
    ru
  }

  /** Filters events from `this` reactive value using a specified predicate `p`.
   *
   *  Only events from `this` for which `p` returns `true` are emitted on the resulting reactive.
   *
   *  @param p          the predicate used to filter events
   *  @return           a subscription and a reactive with the filtered events
   */
  def filter(p: T => Boolean): Reactive[T] with Reactive.Subscription = {
    val rf = new Reactive.Filter[T](self, p)
    rf.subscription = self onReaction rf
    rf
  }

  /** Returns a new reactive that maps events from `this` reactive using the mapping function `f`.
   *
   *  @tparam S         the type of the mapped events
   *  @param f          the mapping function
   *  @return           a subscription and reactive value with the mapped events
   */
  def map[@spec(Int, Long, Double) S](f: T => S): Reactive[S] with Reactive.Subscription = {
    val rm = new Reactive.Map[T, S](self, f)
    rm.subscription = self onReaction rm
    rm
  }

  /* higher-order combinators */

  /** Returns events from the last reactive value that `this` emitted as an event of its own,
   *  in effect multiplexing the nested reactives.
   *
   *  The resulting reactive only emits events from the reactive value last emitted by `this`,
   *  the preceding reactive values are ignored.
   *
   *  This combinator is only available if this reactive value emits events
   *  that are themselves reactive values.
   *
   *  Example:
   *
   *  {{{
   *  val currentReactive = new Reactive.Emitter[Reactive[Int]]
   *  val e1 = new Reactive.Emitter[Int]
   *  val e2 = new Reactive.Emitter[Int]
   *  val currentEvent = currentReactive.mux()
   *  val prints = currentEvent.onEvent(println) 
   *  
   *  currentReactive += e1
   *  e2 += 1 // nothing is printed
   *  e1 += 2 // 2 is printed
   *  currentReactive += e2
   *  e2 += 6 // 6 is printed
   *  e1 += 7 // nothing is printed
   *  }}}
   *
   *  Shown on the diagram:
   *
   *  {{{
   *  time            ------------------->
   *  currentReactive --e1------e2------->
   *  e1              --------2----6----->
   *  e2              -----1----------7-->
   *  currentEvent    --------2----6----->
   *  }}}
   *
   *  '''Use case:'''
   *
   *  {{{
   *  def mux[S](): Reactive[S]
   *  }}}
   *
   *  @tparam S          the type of the events in the nested reactive
   *  @param evidence    an implicit evidence that `this` reactive is nested --
   *                     it emits events of type `T` that is actually a `Reactive[S]`
   *  @return            a reactive of events from the reactive last emitted by `this`
   */
  def mux[@spec(Int, Long, Double) S]()(implicit evidence: T <:< Reactive[S]): Reactive[S] = {
    new Reactive.Mux[T, S](this, evidence)
  }

}


/** Contains useful `Reactive` implementations and factory methods.
 */
object Reactive {

  implicit class ReactiveOps[@spec(Int, Long, Double) T](val self: Reactive[T]) {
    /** Given an initial event `init`, converts this reactive into a `Signal`.
     *
     *  The resulting signal initially contains the event `init`,
     *  and subsequently any event that the `this` reactive produces.
     *
     *  @param init      an initial value for the signal
     *  @return          the signal version of the current reactive
     */
    def signal(init: T) = self.scanPast(init) {
      (cached, value) => value
    }

    /** If the current reactive is a signal already this method downcasts it,
     *  otherwise it lifts it into a signal with the initial value `init`.
     *
     *  @param init      optional value to use when converting the reactive to a signal
     *  @return          the signal version of the current reactive
     */
    def asSignalOrElse(init: T) = self match {
      case s: Signal[T] => s
      case _ => signal(init)
    }

    /** Downcasts this reactive into a signal.
     *
     *  Throws an exception if the current reactive is not a signal.
     *
     *  @return          the signal version of the current reactive
     */
    def asSignal = this match {
      case s: Signal[T] => s
      case _ => throw new UnsupportedOperationException("This is not a signal.")
    }

    /** Creates a union of `this` and `that` reactive.
     *  
     *  The resulting reactive value emits events from both `this` and `that` reactive.
     *  It unreacts when both `this` and `that` reactive unreact.
     *
     *  @param that      another reactive value for the union
     *  @return          a subscription and the reactive value with unified events from `this` and `that`
     */
    def union(that: Reactive[T]): Reactive[T] with Reactive.Subscription = {
      val ru = new Reactive.Union(self, that)
      ru.selfSubscription = self onReaction ru.selfReactor
      ru.thatSubscription = that onReaction ru.thatReactor
      ru.subscription = Reactive.CompositeSubscription(ru.selfSubscription, ru.thatSubscription)
      ru
    }
  
    /** Creates a concatenation of `this` and `that` reactive.
     *
     *  The resulting reactive value produces all the events from `this` reactive
     *  until `this` unreacts, and then outputs all the events from `that`
     *  that happened before and after `this` unreacted.
     *  To do this, this operation potentially caches all the events from `that`.
     *  When `that` unreacts, the resulting reactive value unreacts.
     *
     *  '''Use case:'''
     *
     *  {{{
     *  def concat(that: Reactive[T]): Reactive[T]
     *  }}}
     *
     *  @param that      another reactive value for the concatenation
     *  @note This operation potentially caches events from `that`.
     *  Unless certain that `this` eventually unreacts, `concat` should not be used.
     *  To enforce this, clients must import the `CanBeBuffered` evidence explicitly
     *  into the scope in which they call `concat`.
     *  
     *  @param a         evidence that arrays can be created for the type `T`
     *  @param b         evidence that the client allows events from `that` to be buffered
     *  @return          a subscription and a reactive value that concatenates events from `this` and `that`
     */
    def concat(that: Reactive[T])(implicit a: Arrayable[T], b: CanBeBuffered): Reactive[T] with Reactive.Subscription = {
      val rc = new Reactive.Concat(self, that, a)
      rc.selfSubscription = self onReaction rc.selfReactor
      rc.thatSubscription = that onReaction rc.thatReactor
      rc.subscription = Reactive.CompositeSubscription(rc.selfSubscription, rc.thatSubscription)
      rc
    }
  
    /** Syncs the arrival of events from `this` and `that` reactive value.
     *  
     *  Ensures that pairs of events from this reactive value and that reactive value
     *  are emitted together.
     *  If the events produced in time by `this` and `that`, the sync will be as follows:
     *
     *  {{{
     *  time   --------------------------->
     *  this   ----1---------2-------4---->
     *  that   --1-----2--3--------------->
     *  sync   ----1,1-------2,2-----4,3-->
     *  }}}
     *
     *  Pairs of events produced from `this` and `that` are then transformed using
     *  specified function `f`.
     *  For example, clients that want to output tuples do:
     *
     *  {{{
     *  val synced = (a sync b) { (a, b) => (a, b) }
     *  }}}
     *
     *  Clients that, for example, want to create differences in pairs of events do:
     *
     *  {{{
     *  val diffs = (a sync b)(_ - _)
     *  }}}
     *
     *  The resulting reactive unreacts either when
     *  `this` unreacts and there are no more buffered events from this,
     *  or when `that` unreacts and there are no more buffered events from `that`.
     *
     *  '''Use case:'''
     *
     *  {{{
     *  def sync[S, R](that: Reactive[S])(f: (T, S) => R): Reactive[R]
     *  }}}
     *
     *  @note This operation potentially caches events from `this` and `that`.
     *  Unless certain that both `this` produces a bounded number of events
     *  before the `that` produces an event, and vice versa, this operation should not be called.
     *  To enforce this, clients must import the `CanBeBuffered` evidence explicitly
     *  into the scope in which they call `sync`.
     *
     *  @tparam S         the type of the events in `that` reactive
     *  @tparam R         the type of the events in the resulting reactive
     *  @param that       the reactive to sync with
     *  @param f          the mapping function for the pair of events
     *  @param at         evidence that arrays can be created for the type `T`
     *  @param as         evidence that arrays can be created for the type `S`
     *  @param b          evidence that the client allows events to be buffered
     *  @return           a subscription and the reactive with the resulting events
     */
    def sync[@spec(Int, Long, Double) S, @spec(Int, Long, Double) R](that: Reactive[S])(f: (T, S) => R)
      (implicit at: Arrayable[T], as: Arrayable[S], b: CanBeBuffered): Reactive[R] with Reactive.Subscription = {
      val rs = new Reactive.Sync(self, that, f, at, as)
      rs.selfSubscription = self onReaction rs.selfReactor
      rs.thatSubscription = that onReaction rs.thatReactor
      rs.subscription = Reactive.CompositeSubscription(rs.selfSubscription, rs.thatSubscription)
      rs
    }

    /* higher-order combinators */

    /** Unifies the events produced by all the reactives emitted by `this`.
     *
     *  This operation is only available for reactive values that emit
     *  other reactives as events.
     *  The resulting reactive unifies events of all the reactives emitted by `this`.
     *  Once `this` and all the reactives emitted by `this` unreact, the resulting reactive terminates.
     *
     *  Example:
     *  
     *  {{{
     *  time  -------------------------->
     *  this     --1----2--------3------>
     *               ---------5----6---->
     *                 ---4----------7-->
     *  union -----1----2-4---5--3-6-7-->
     *  }}}
     *  
     *  '''Use case:'''
     *
     *  {{{
     *  def union[S](): Reactive[S]
     *  }}}
     *
     *  @tparam S         the type of the events in reactives emitted by `this`
     *  @param evidence   evidence that events of type `T` produced by `this` are
     *                    actually reactive values of type `S`
     *  @return           a subscription and the reactive with the union of all the events
     *  
     */
    def union[@spec(Int, Long, Double) S]()(implicit evidence: T <:< Reactive[S]): Reactive[S] with Reactive.Subscription = {
      new Reactive.PostfixUnion[T, S](self, evidence)
    }

    /** Concatenates the events produced by all the reactives emitted by `this`.
     *
     *  This operation is only available for reactive values that emit
     *  other reactives as events.
     *  Once `this` and all the reactives unreact, this reactive unreacts.
     *
     *  '''Use case:'''
     *
     *  {{{
     *  def concat[S](): Reactive[S]
     *  }}}
     *
     *  @note This operation potentially buffers events from the nested reactives.
     *  Unless each reactive emitted by `this` is known to unreact eventually,
     *  this operation should not be called.
     *  To enforce this, clients are required to import the `CanBeBuffered` evidence
     *  explicitly into the scope in which they call `concat`.
     *  
     *  @tparam S         the type of the events in reactives emitted by `this`
     *  @param evidence   evidence that events of type `T` produced by `this` are
     *                    actually reactive values of type `S`
     *  @param a          evidence that arrays can be created for type `S`
     *  @param b          evidence that buffering events is allowed
     *  @return           a subscription and the reactive that concatenates all the events
     */
    def concat[@spec(Int, Long, Double) S]()(implicit evidence: T <:< Reactive[S], a: Arrayable[S], b: CanBeBuffered): Reactive[S] with Reactive.Subscription = {
      val pc = new Reactive.PostfixConcat[T, S](self, evidence)
      pc.subscription = self onReaction pc
      pc
    }
  }

  private[reactress] class Foreach[@spec(Int, Long, Double) T]
    (val self: Reactive[T], val f: T => Unit)
  extends Reactive.Default[Unit] with Reactor[T] with Reactive.ProxySubscription {
    def react(value: T) {
      f(value)
      reactAll(())
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactress] class ScanPast[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val z: S, val op: (S, T) => S)
  extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    private var cached: S = _
    def init(z: S) {
      cached = z
    }
    init(z)
    def apply() = cached
    def react(value: T) {
      cached = op(cached, value)
      reactAll(cached)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactress] class Mutate[@spec(Int, Long, Double) T, M <: ReactMutable]
    (val self: Reactive[T], val mutable: M, val mutation: T => Unit)
  extends Reactor[T] with Reactive.ProxySubscription {
    def react(value: T) = {
      mutation(value)
      mutable.onMutated()
    }
    def unreact() {}
    var subscription = Subscription.empty
  }

  private[reactress] class MutateMany[@spec(Int, Long, Double) T, M <: ReactMutable]
    (val self: Reactive[T], val mutables: Seq[M], val mutation: T => Unit)
  extends Reactor[T] with Reactive.ProxySubscription {
    def react(value: T) = {
      mutation(value)
      for (m <- mutables) m.onMutated()
    }
    def unreact() {}
    var subscription = Subscription.empty
  }

  private[reactress] class After[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val that: Reactive[S])
  extends Reactive.Default[T] with Reactive.ProxySubscription {
    var started = false
    var live = true
    def unreactBoth() = if (live) {
      live = false
      unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) {
        if (started) reactAll(value)
      }
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[S] {
      def react(value: S) {
        started = true
      }
      def unreact() = if (!started) unreactBoth()
    }
    var selfSubscription = Subscription.empty
    var thatSubscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactress] class Until[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val that: Reactive[S])
  extends Reactive.Default[T] with Reactive.ProxySubscription {
    var live = true
    def unreactBoth() = if (live) {
      live = false
      unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) = if (live) reactAll(value)
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[S] {
      def react(value: S) = unreactBoth()
      def unreact() {}
    }
    var selfSubscription: Reactive.Subscription = Subscription.empty
    var thatSubscription: Reactive.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactress] class Union[@spec(Int, Long, Double) T](val self: Reactive[T], val that: Reactive[T])
  extends Reactive.Default[T] with Reactive.ProxySubscription {
    var live = 2
    def unreactBoth() = {
      live -= 1
      if (live == 0) unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) = reactAll(value)
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[T] {
      def react(value: T) = reactAll(value)
      def unreact() = unreactBoth()
    }
    var selfSubscription: Reactive.Subscription = Subscription.empty
    var thatSubscription: Reactive.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactress] class Concat[@spec(Int, Long, Double) T](val self: Reactive[T], val that: Reactive[T], val a: Arrayable[T])
  extends Reactive.Default[T] with Reactive.ProxySubscription {
    var selfLive = true
    var thatLive = true
    val buffer = new UnrolledBuffer[T]()(a)
    def unreactBoth() {
      if (!selfLive && !thatLive) {
        unreactAll()
      }
    }
    def flush() {
      buffer.foreach(v => reactAll(v))
      buffer.clear()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) = reactAll(value)
      def unreact() {
        selfLive = false
        flush()
        unreactBoth()
      }
    }
    val thatReactor = new Reactor[T] {
      def react(value: T) = {
        if (selfLive) buffer += value
        else reactAll(value)
      }
      def unreact() {
        thatLive = false
        unreactBoth()
      }
    }
    var selfSubscription: Reactive.Subscription = Subscription.empty
    var thatSubscription: Reactive.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactress] class Sync[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, @spec(Int, Long, Double) R]
    (val self: Reactive[T], val that: Reactive[S], val f: (T, S) => R, val at: Arrayable[T], val as: Arrayable[S])
  extends Reactive.Default[R] with Reactive.ProxySubscription {
    val tbuffer = new UnrolledBuffer[T]()(at)
    val sbuffer = new UnrolledBuffer[S]()(as)
    def unreactBoth() {
      tbuffer.clear()
      sbuffer.clear()
      unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(tvalue: T) {
        if (sbuffer.isEmpty) tbuffer += tvalue
        else {
          val svalue = sbuffer.dequeue()
          reactAll(f(tvalue, svalue))
        }
      }
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[S] {
      def react(svalue: S) = {
        if (tbuffer.isEmpty) sbuffer += svalue
        else {
          val tvalue = tbuffer.dequeue()
          reactAll(f(tvalue, svalue))
        }
      }
      def unreact() = unreactBoth()
    }
    var selfSubscription: Reactive.Subscription = Subscription.empty
    var thatSubscription: Reactive.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactress] class Filter[@spec(Int, Long, Double) T](val self: Reactive[T], val p: T => Boolean)
  extends Reactive.Default[T] with Reactor[T] with Reactive.ProxySubscription {
    def react(value: T) {
      if (p(value)) reactAll(value)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactress] class Map[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](val self: Reactive[T], val f: T => S)
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    def react(value: T) {
      reactAll(f(value))
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactress] class Mux[T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val evidence: T <:< Reactive[S])
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    muxed =>
    private[reactress] var currentSubscription: Subscription = null
    private[reactress] var terminated = false
    def newReactor: Reactor[S] = new Reactor[S] {
      def react(value: S) = reactAll(value)
      def unreact() {
        currentSubscription = Subscription.empty
        checkUnreact()
      }
    }
    def checkUnreact() = if (terminated && currentSubscription == Subscription.empty) unreactAll()
    def react(value: T) {
      val nextReactive = evidence(value)
      currentSubscription.unsubscribe()
      currentSubscription = nextReactive onReaction newReactor
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
      currentSubscription = Subscription.empty
      subscription = self onReaction this
    }
    init(evidence)
  }

  private[reactress] class PostfixUnion[T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val evidence: T <:< Reactive[S])
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    union =>
    private[reactress] var subscriptions = mutable.Map[Reactive[S], Subscription]()
    private[reactress] var terminated = false
    def checkUnreact() = if (terminated && subscriptions.isEmpty) unreactAll()
    def newReactor(r: Reactive[S]) = new Reactor[S] {
      def react(value: S) = reactAll(value)
      def unreact() = {
        subscriptions.remove(r)
        checkUnreact()
      }
    }
    def react(value: T) {
      val nextReactive = evidence(value)
      if (!subscriptions.contains(nextReactive)) {
        val sub = nextReactive onReaction newReactor(nextReactive)
        subscriptions(nextReactive) = sub
      }
    }
    def unreact() {
      terminated = true
      checkUnreact()
    }
    override def unsubscribe() {
      for (kv <- subscriptions) kv._2.unsubscribe()
      subscriptions.clear()
      super.unsubscribe()
    }
    val subscription = self onReaction this
  }

  private[reactress] final class ConcatEntry[S](var subscription: Subscription, var buffer: UnrolledBuffer[S], var live: Boolean) {
    def ready = buffer == null
  }

  private[reactress] class PostfixConcat[T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val evidence: T <:< Reactive[S])(implicit val arrayable: Arrayable[S])
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    union =>
    private[reactress] var subscriptions = new UnrolledBuffer[ConcatEntry[S]]()
    private[reactress] var terminated = false
    private def checkUnreact() = if (terminated && subscriptions.isEmpty) unreactAll()
    def moveToNext() {
      if (subscriptions.isEmpty) checkUnreact()
      else {
        val entry = subscriptions.head
        val buffer = entry.buffer
        entry.buffer = null
        while (buffer.nonEmpty) reactAll(buffer.dequeue())
        if (!entry.live) {
          subscriptions.dequeue()
          moveToNext()
        }
      }
    }
    def newReactor(entry: ConcatEntry[S]) = new Reactor[S] {
      def react(value: S) {
        if (entry.ready) reactAll(value)
        else entry.buffer.enqueue(value)
      }
      def unreact() {
        if (entry.ready) {
          subscriptions.dequeue()
          moveToNext()
        } else entry.live = false
      }
    }
    def react(value: T) {
      val nextReactive = evidence(value)
      val entry = new ConcatEntry(null, new UnrolledBuffer[S], true)
      entry.subscription = nextReactive onReaction newReactor(entry)
      subscriptions.enqueue(entry)
      if (!subscriptions.head.ready) moveToNext()
    }
    def unreact() {
      terminated = true
      checkUnreact()
    }
    override def unsubscribe() {
      while (subscriptions.nonEmpty) {
        subscriptions.head.subscription.unsubscribe()
        subscriptions.dequeue()
      }
      super.unsubscribe()
    }
    var subscription = Subscription.empty
  }

  /** A base trait for reactives that never emit events.
   *
   *  @tparam T         type of events never emitted by this reactive
   */
  trait Never[@spec(Int, Long, Double) T]
  extends Reactive[T] {
    def hasSubscriptions = false
    def onReaction(reactor: Reactor[T]) = {
      reactor.unreact()
      Subscription.empty
    }
    def reactAll(value: T) {}
    def unreactAll() {}
  }

  private object NeverImpl extends Never[Nothing]

  /** A reactive that never emits events.
   * 
   *  @tparam T         type of events in this reactive
   */
  def Never[T] = NeverImpl.asInstanceOf[Reactive[T]]

  // TODO Amb

  /** The proxy reactive that emits events of its underlying reactive.
   *
   *  @tparam T         type of the proxy signal
   */
  trait Proxy[@spec(Int, Long, Double) T]
  extends Reactive[T] {
    val underlying: Reactive[T]
    def hasSubscriptions = underlying.hasSubscriptions
    def onReaction(r: Reactor[T]) = underlying.onReaction(r)
  }

  /** A subscription to a certain kind of event,
   *  event processing or computation in general.
   *  Calling `unsubscribe` on the subscription
   *  causes the events to no longer be propagated
   *  to this particular subscription or some computation to cease.
   *
   *  Unsubscribing is idempotent -- calling `unsubscribe` second time does nothing.
   */
  trait Subscription {
    def unsubscribe(): Unit
  }

  /** Contains factory methods for subscription.
   */
  object Subscription {
    /** Unsubscribing does nothing. */
    val empty = new Subscription {
      def unsubscribe() {}
    }
    /** Invokes the specified `onUnsubscribe` block when `unsubscribe` is called.
     *
     *  @param onUnsubscribe     code to execute when `unsubscribe` is called
     */
    def apply(onUnsubscribe: =>Unit) = new Subscription {
      def unsubscribe() = onUnsubscribe
    }
  }

  /** Unsubscribes by calling `unsubscribe` on the underlying subscription.
   */
  trait ProxySubscription extends Subscription {
    def subscription: Subscription
    def unsubscribe() {
      subscription.unsubscribe()
    }
  }

  /** Unsubscribing will call `unsubscribe` on all the 
   *  subscriptions in `ss`.
   *
   *  @param ss         the child subscriptions
   *  @return           the composite subscription
   */
  def CompositeSubscription(ss: Subscription*): Subscription = new Subscription {
    def unsubscribe() {
      for (s <- ss) s.unsubscribe()
    }
  }

  private val bufferUpperBound = 8
  private val hashTableLowerBound = 5

  /** The default implementation of a reactive value.
   *
   *  Keeps an optimized weak collection of weak references to subscribers.
   *  References to subscribers that are no longer reachable in the application
   *  will be removed eventually.
   *
   *  @tparam T       type of the events in this reactive value
   */
  trait Default[@spec(Int, Long, Double) T] extends Reactive[T] {
    private[reactress] var demux: AnyRef = null
    private[reactress] var unreacted: Boolean = false
    def onReaction(reactor: Reactor[T]) = {
      if (unreacted) {
        reactor.unreact()
        Subscription.empty
      } else {
        demux match {
          case null =>
            demux = new WeakRef(reactor)
          case w: WeakRef[Reactor[T] @unchecked] =>
            val wb = new WeakBuffer[Reactor[T]]
            wb.addRef(w)
            wb.addEntry(reactor)
            demux = wb
          case wb: WeakBuffer[Reactor[T] @unchecked] =>
            if (wb.size < bufferUpperBound) {
              wb.addEntry(reactor)
            } else {
              val wht = toHashTable(wb)
              wht.addEntry(reactor)
              demux = wht
            }
          case wht: WeakHashTable[Reactor[T] @unchecked] =>
            wht.addEntry(reactor)
        }
        newSubscription(reactor)
      }
    }
    private def newSubscription(r: Reactor[T]) = new Subscription {
      onSubscriptionChange()
      def unsubscribe() {
        removeReaction(r)
      }
    }
    private def removeReaction(r: Reactor[T]) {
      demux match {
        case null =>
          // nothing to invalidate
        case w: WeakRef[Reactor[T] @unchecked] =>
          if (w.get eq r) w.clear()
        case wb: WeakBuffer[Reactor[T] @unchecked] =>
          wb.invalidateEntry(r)
        case wht: WeakHashTable[Reactor[T] @unchecked] =>
          wht.invalidateEntry(r)
      }
      onSubscriptionChange()
    }
    def reactAll(value: T) {
      demux match {
        case null =>
          // no need to inform anybody
        case w: WeakRef[Reactor[T] @unchecked] =>
          val r = w.get
          if (r != null) r.react(value)
          else demux = null
        case wb: WeakBuffer[Reactor[T] @unchecked] =>
          bufferReactAll(wb, value)
        case wht: WeakHashTable[Reactor[T] @unchecked] =>
          tableReactAll(wht, value)
      }
    }
    def unreactAll() {
      unreacted = true
      demux match {
        case null =>
          // no need to inform anybody
        case w: WeakRef[Reactor[T] @unchecked] =>
          val r = w.get
          if (r != null) r.unreact()
          else demux = null
        case wb: WeakBuffer[Reactor[T] @unchecked] =>
          bufferUnreactAll(wb)
        case wht: WeakHashTable[Reactor[T] @unchecked] =>
          tableUnreactAll(wht)
      }
    }
    private def checkBuffer(wb: WeakBuffer[Reactor[T]]) {
      if (wb.size <= 1) {
        if (wb.size == 1) demux = new WeakRef(wb(0))
        else if (wb.size == 0) demux = null
      }
    }
    private def bufferReactAll(wb: WeakBuffer[Reactor[T]], value: T) {
      val array = wb.array
      var until = wb.size
      var i = 0
      while (i < until) {
        val ref = array(i)
        val r = ref.get
        if (r ne null) {
          r.react(value)
          i += 1
        } else {
          wb.removeEntryAt(i)
          until -= 1
        }
      }
      checkBuffer(wb)
    }
    private def bufferUnreactAll(wb: WeakBuffer[Reactor[T]]) {
      val array = wb.array
      var until = wb.size
      var i = 0
      while (i < until) {
        val ref = array(i)
        val r = ref.get
        if (r ne null) {
          r.unreact()
          i += 1
        } else {
          wb.removeEntryAt(i)
          until -= 1
        }
      }
      checkBuffer(wb)
    }
    private def toHashTable(wb: WeakBuffer[Reactor[T]]) = {
      val wht = new WeakHashTable[Reactor[T]]
      val array = wb.array
      val until = wb.size
      var i = 0
      while (i < until) {
        val r = array(i).get
        if (r != null) wht.addEntry(r)
        i += 1
      }
      wht
    }
    private def toBuffer(wht: WeakHashTable[Reactor[T]]) = {
      val wb = new WeakBuffer[Reactor[T]](bufferUpperBound)
      val table = wht.table
      var i = 0
      while (i < table.length) {
        var ref = table(i)
        if (ref != null && ref.get != null) wb.addRef(ref)
        i += 1
      }
      wb
    }
    private def checkHashTable(wht: WeakHashTable[Reactor[T]]) {
      if (wht.size < hashTableLowerBound) {
        val wb = toBuffer(wht)
        demux = wb
        checkBuffer(wb)
      }
    }
    private def tableReactAll(wht: WeakHashTable[Reactor[T]], value: T) {
      val table = wht.table
      var i = 0
      while (i < table.length) {
        val ref = table(i)
        if (ref ne null) {
          val r = ref.get
          if (r ne null) r.react(value)
          else wht.removeEntryAt(i, null)
        }
        i += 1
      }
      checkHashTable(wht)
    }
    private def tableUnreactAll(wht: WeakHashTable[Reactor[T]]) {
      val table = wht.table
      var i = 0
      while (i < table.length) {
        val ref = table(i)
        if (ref ne null) {
          val r = ref.get
          if (r ne null) r.unreact()
          else wht.removeEntryAt(i, null)
          i += 1
        }
      }
      checkHashTable(wht)
    }
    def hasSubscriptions: Boolean = demux != null
    def onSubscriptionChange() {}
  }

  /** A reactive value that can programatically emit events.
   *
   *  Events are emitted to the reactive value by calling the `+=` method.
   *  The emitter can be closed by calling the `close` method --
   *  after this no more events will be accepted through `+=`.
   *
   *  Example:
   *  
   *  {{{
   *  val emitter = new Reactive.Emitter[Int]
   *  val prints = emitter.onEvent(println)
   *  emitter += 1
   *  emitter += 2
   *  }}}
   *
   *  @tparam       the type of events that this emitter can emit
   */
  class Emitter[@spec(Int, Long, Double) T]
  extends Reactive[T] with Default[T] {
    private var live = true
    def +=(value: T) {
      if (live) reactAll(value)
    }
    def close(): Unit = if (live) {
      live = false
      unreactAll()
    }
  }

  /** A reactive emitter that can be used with the `mutate` block.
   *  
   *  Calling `mutate` with this bind emitter will add a subscription
   *  to the emitter that can unsubscribe from that `mutate` statement.
   *
   *  For most purposes, clients should just use the regular `Reactive.Emitter`.
   *
   *  @tparam T     the type of events in the bind emitter
   */
  class BindEmitter[@spec(Int, Long, Double) T]
  extends Reactive[T] with Default[T] with ReactMutable.Subscriptions {
    private var live = true
    def +=(value: T) {
      if (live) reactAll(value)
    }
    def close(): Unit = if (live) {
      live = false
      unreactAll()
    }
  }

  /** Creates a new reactive that invokes the function `f` on any `Reactor` that subscribes to it.
   *
   *  Passive reactives usually emit separate event streams to each reactor
   *  for which `onReaction` has been called.
   *  
   *  Once all the events are emitted, clients should call `unreact` to notify
   *  the reactor that there will be no more events.
   *
   *  Example:
   *
   *  {{{
   *  val r = passive[Int] { reactor =>
   *    reactor.react(1)
   *    reactor.react(2)
   *    reactor.unreact()
   *  }
   *  }}}
   *
   *  Never use `passive` to install adapt a callback in a 3rd party API.
   *  For example, *never* do this:
   *
   *  {{{
   *  val r = passive[String] { reactor =>
   *    Future { "... " * 5 + "done!" } onSuccess {
   *      case e => reactor.react(e); reactor.unreact()
   *    }
   *  }
   *  val m = Signal.Mutable(mutable.ArrayBuffer[String]())
   *  val s1 = r.onEvent(e => m += e)
   *  val s2 = r.onEvent(e => m += e)
   *  }}}
   *
   *  Above, subscriptions `s1` and `s2` could execute concurrenctly and corrupt the buffer.
   *
   *  @note You should never use this method to bind reactors to bind reactors to custom callbacks
   *        in 3rd party APIs. Those callbacks may execute on another thread, and that is *unsafe*.
   *
   *  @tparam T         type of the events in this passive reactive
   *  @param f          function to execute on any newly subscribed reactor
   *  @return           the passive reactive defined by `f`
   */
  def passive[@spec(Int, Long, Double) T](f: Reactor[T] => Subscription): Reactive[T] = {
    new Reactive[T] {
      def hasSubscriptions = false
      def reactAll(value: T) {}
      def unreactAll() {}
      def onReaction(r: Reactor[T]) = f(r)
    }
  }

  /** Defines a new passive reactive that emits a single event
   *  to any new subscriber.
   *
   *  @tparam T         type of the event to emit
   *  @param event      event to emit
   *  @return           a reactive that emits a single event
   */
  def single[@spec(Int, Long, Double) T](event: T): Reactive[T] = passive[T] { r =>
    var cancelled = false
    r.react(event)
    if (!cancelled) r.unreact()
    Subscription { cancelled = true }
  }

  /** Defines a new passive reactive that emits several events
   *  to any new subscriber.
   *
   *  @tparam T         type of the events to emit
   *  @param events     events to emit
   *  @return           a reactive that emits the specified events
   */
  def items[@spec(Int, Long, Double) T](events: Array[T]): Reactive[T] = passive[T] { r =>
    var cancelled = false
    var it = events.iterator
    while (!cancelled && it.hasNext) {
      r.react(it.next())
    }
    if (!cancelled) r.unreact()
    Subscription { cancelled = true }
  }

}

