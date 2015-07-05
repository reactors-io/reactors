package scala.reactive



import scala.annotation.tailrec
import scala.collection._
import scala.reactive.calc.RVFun
import scala.reactive.util._



/** A basic event stream.
 *
 *  Event stream are special objects that may
 *  produce events of a certain type `T`.
 *  Clients may subscribe side-effecting functions (i.e. callbacks)
 *  to these events with `onReaction`, `onEvent`, `onCase` and `on` --
 *  each of these methods will invoke the callback when an event
 *  is produced, but some may be more suitable depending on the use-case.
 *
 *  An event stream produces events until it ''unreacts''.
 *  After the event stream unreacts, it never produces an event again.
 *  
 *  Event streams can also be manipulated using declarative combinators
 *  such as `map`, `filter`, `until`, `after` and `scanPast`:
 *
 *  {{{
 *  def positiveSquares(r: Events[Int]) = r.map(x => x * x).filter(_ != 0)
 *  }}}
 *
 *  With the exception of `onX` family of methods,
 *  operators passed to these declarative combinators should be pure --
 *  they should not have any side-effects.
 *
 *  The result of applying a declarative combinator on `Events[T]` is usually
 *  another `Events[S]`, possibly with a different type parameter.
 *  Most declarative combinators return a `Subscription` object used to
 *  `unsubscribe` from their event source.
 *  This is not necessary in most situations,
 *  but can be used to prune the dataflow graph.
 *
 *  Event streams are specialized for `Int`, `Long` and `Double`.
 *
 *  Every event stream is bound to a specific `Isolate`.
 *  An event stream will only produce events during the execution of that
 *  isolate -- events are never triggered on a different isolate.
 *  It is forbidden to share event streams between isolates.
 *
 *  @author        Aleksandar Prokopec
 *
 *  @tparam T      type of the events in this event stream
 */
trait Events[@spec(Int, Long, Double) +T] {
  self =>

  /** Is there any other event stream that depends on the events
   *  produced by this event stream.
   *
   *  Other event streams will return `true` if there are any subscribers attached
   *  to them.
   *  This method is used internally to optimize and recycle some subscriptions
   *  away.
   */
  private[reactive] def hasSubscriptions: Boolean

  /** Attaches a new `reactor` to this event stream,
   *  which is called multiple times whenever an event is produced,
   *  or an exception occurs,
   *  and once when the event stream is terminated.
   *
   *  @param reactor     the reactor that accepts `react` and `unreact` events
   *  @return            a subscription for unsubscribing from reactions
   */
  def observe(reactor: Reactor[T]): Events.Subscription

  /** Attaches a reactor to this event stream,
   *  which is called multiple times whenever an event is produced,
   *  or an exception occurs,
   *  and once when the event stream is terminated.
   *
   *  @param r           the reactor
   *  @return            a subscription for unsubscribing from reactions
   */
  def onReaction(r: Reactor[T])(implicit canLeak: CanLeak): Events.Subscription = {
    val eventSink = new Reactor.EventSink(r, canLeak)
    eventSink.subscription = observe(eventSink)
    Events.Subscription.registerLeakySubscription(canLeak, eventSink.subscription)
    Events.Subscription.liftSubscription(canLeak, eventSink.subscription)
  }

  /** A shorthand for `onReaction` -- the specified functions are invoked
   *  whenever there is an event or an unreaction.
   *
   *  @param reactFunc   called when this event stream produces an event
   *  @param unreactFunc called when this event stream unreacts
   *  @return            a subscription for unsubscribing from reactions
   */
  def onReactUnreact(reactFunc: T => Unit)(unreactFunc: =>Unit)
    (implicit canLeak: CanLeak): Events.Subscription = {
    onReaction(new Reactor[T] {
      def react(event: T) {
        try reactFunc(event)
        catch ignoreNonLethal
      }
      def except(t: Throwable) {}
      def unreact() {
        try unreactFunc
        catch ignoreNonLethal
      }
    })
  }

  /** A shorthand for `onReaction` -- the specified function is invoked whenever
   *  there is an event.
   *
   *  @param reactor     the callback for events
   *  @return            a subcriptions for unsubscribing from reactions
   */
  def onEvent(reactor: T => Unit)(implicit canLeak: CanLeak):
    Events.Subscription = {
    onReaction(new Reactor[T] {
      def react(event: T) {
        try reactor(event)
        catch ignoreNonLethal
      }
      def except(t: Throwable) {}
      def unreact() {}
    })
  }

  /** A shorthand for `onReaction` -- the specified partial function is applied
   *  to only those events for which it is defined.
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
   *  def onCase(reactor: PartialFunction[T, Unit]): Events.Subscription
   *  }}}
   *  
   *  @param reactor     the callback for those events for which it is defined
   *  @return            a subscription for unsubscribing from reactions
   */
  def onCase(reactor: PartialFunction[T, Unit])
    (implicit sub: T <:< AnyRef, canLeak: CanLeak): Events.Subscription = {
    onReaction(new Reactor[T] {
      def react(event: T) = {
        try {
          if (reactor.isDefinedAt(event)) reactor(event)
        } catch ignoreNonLethal
      }
      def except(t: Throwable) {}
      def unreact() {}
    })
  }

  /** A shorthand for `onReaction` -- called whenever an event occurs.
   *
   *  This method is handy when the precise event is not important,
   *  or the type of the event is `Unit`.
   *
   *  @param reactor     the callback invoked when an event arrives
   *  @return            a subscription for unsubscribing from reactions
   */
  def on(reactor: =>Unit)(implicit canLeak: CanLeak): Events.Subscription = {
    onReaction(new Reactor[T] {
      def react(value: T) {
        try reactor
        catch ignoreNonLethal
      }
      def except(t: Throwable) {}
      def unreact() {}
    })
  }

  /** Executes the specified block when `this` event stream unreacts.
   *
   *  @param f           the callback invoked when `this` unreacts
   *  @return            a subscription for the unreaction notification
   */
  def onUnreact(reactor: =>Unit)(implicit canLeak: CanLeak): Events.Subscription = {
    onReaction(new Reactor[T] {
      def react(value: T) {}
      def except(t: Throwable) {}
      def unreact() {
        try reactor
        catch ignoreNonLethal
      }
    })
  }

  /** Executes the specified block when `this` event stream forwards an exception.
   *
   *  @param pf          the partial function used to handle the exception
   *  @return            a subscription for the exception notifications
   */
  def onExcept(pf: PartialFunction[Throwable, Unit])(implicit canLeak: CanLeak):
    Events.Subscription = {
    onReaction(new Reactor[T] {
      def react(value: T) {}
      def except(t: Throwable) {
        try {
          if (pf.isDefinedAt(t)) pf(t)
        } catch ignoreNonLethal
      }
      def unreact() {}
    })
  }

  /** Executes the specified function every time an event arrives.
   *
   *  Semantically equivalent to `onEvent`,
   *  but supports `for`-loop syntax with event streams,
   *  and does not cause time and memory leaks.
   *
   *  The resulting event stream emits unit events every time an event arrives.
   *  
   *  {{{
   *  for (event <- r) println("Event arrived: " + event)
   *  }}}
   *
   *  @param f           the callback invoked when an event arrives
   *  @return            a subscription that is also an event stream producing
   *                     `Unit` events after each callback invocation
   */
  def foreach(f: T => Unit): Events[Unit] with Events.Subscription = {
    val rf = new Events.Foreach(self, f)
    rf.subscription = self observe rf
    rf
  }

  /** Executes the specified function every time an event arrives.
   *
   *  Semantically equivalent to `onExcept`,
   *  but does not cause time and memory leaks.
   *  
   *  @param  f          an exception handler
   *  @return            a subscription that is also an event stream producing
   *                     `Unit` events after each callback invocation
   */
  def handle(f: Throwable => Unit):
    Events[Unit] with Events.Subscription = {
    val rh = new Events.Handle(self, f)
    rh.subscription = self observe rh
    rh
  }

  /** Executes when the underlying event stream unreacts.
   *  
   *  Semantically equivalent to `onUnreact`,
   *  but does not cause time and memory leaks.
   *
   *  The resulting event stream emits a unit event when the underlying event stream
   *  unreacts. It then itself unreacts.
   *
   *  @param body        the callback invoked when an event arrives
   *  @return            a subscription that is also an event stream
   */
  def ultimately(body: =>Unit): Events[Unit] with Events.Subscription = {
    val rf = new Events.Ultimately(self, () => body)
    rf.subscription = self observe rf
    rf
  }

  /** Transforms exceptions into events.
   *
   *  If the specified partial function is defined for the exception,
   *  an event is emitted.
   *  Otherwise, the same exception is forwarded.
   *  
   *  @param  pf         partial mapping from functions to events
   *  @return            an event stream that emits events when an exception arrives
   */
  def recover[U >: T](pf: PartialFunction[Throwable, U])
    (implicit evid: U <:< AnyRef): Events[U] with Events.Subscription = {
    val rr = new Events.Recover(self, pf)
    rr.subscription = self observe rr
    rr
  }

  /** Creates a new event stream `s` that produces events by consecutively
   *  applying the specified operator `op` to the previous event that `s`
   *  produced and the current event that this event stream value produced.
   *
   *  The `scanPast` operation allows the current event from this event stream to be
   *  mapped into a different event by looking "into the past", i.e. at the
   *  event previously emitted by the resulting event stream.
   *
   *  Example -- assume that an event stream `r` produces events `1`, `2` and
   *  `3`. The following `s`:
   *
   *  {{{
   *  val s = r.scanPast(0)((sum, n) => sum + n)
   *  }}}
   *
   *  will produce events `1`, `3` (`1 + 2`) and `6` (`3 + 3`).
   *  '''Note:''' the initial value `0` is '''not emitted'''.
   *  
   *  The `scanPast` can also be used to produce an event stream of a different
   *  type. The following produces a complete history of all the events seen so
   *  far:
   *
   *  {{{
   *  val s2 = r.scanPast(List[Int]()) {
   *    (history, n) => n :: history
   *  }
   *  }}}
   *  
   *  The `s2` will produce events `1 :: Nil`, `2 :: 1 :: Nil` and
   *  `3 :: 2 :: 1 :: Nil`.
   *  '''Note:''' the initial value `Nil` is '''not emitted'''.
   *
   *  The resulting event stream is not only an event stream, but also a
   *  `Signal`, so the value of the previous event can be obtained by calling
   *  `apply` at any time.
   *
   *  This operation is closely related to a `scanLeft` on a collection --
   *  if an event stream were a sequence of elements, then `scanLeft` would
   *  produce a new sequence whose elements correspond to the events of the
   *  resulting event stream.
   *
   *  @tparam S        the type of the events in the resulting event stream
   *  @param z         the initial value of the scan past
   *  @param op        the operator the combines the last produced and the
   *                   current event into a new one
   *  @return          a subscription that is also an event stream that scans
   *                   events from `this` event stream
   */
  def scanPast[@spec(Int, Long, Double) S](z: S)(op: (S, T) => S):
    Signal[S] with Events.Subscription = {
    val r = new Events.ScanPast(self, z, op)
    r.subscription = self observe r
    r
  }

  /** Checks if this event stream is also a signal.
   *
   *  @return         `true` if the event stream is a signal, `false` otherwise
   */
  def isSignal: Boolean = this match {
    case s: Signal[T] => true
    case _ => false
  }

  /** Mutates the target mutable event stream called `mutable` each time `this`
   *  event stream produces an event.
   *
   *  One type of a mutable event stream is a mutable signal (`Signal.Mutable`),
   *  which is a wrapper for regular mutable objects.
   *  Here is an example, given an event stream of type `r`:
   *
   *  {{{
   *  val eventLog = Signal.Mutable(mutable.Buffer[String]())
   *  val eventLogMutations = r.mutate(eventLog) { event =>
   *    eventLog() += "at " + System.nanoTime + ": " + event
   *  } // <-- eventLog event propagated
   *  }}}
   *
   *  Whenever an event arrives on `r`, an entry is added to the buffer
   *  underlying `eventLog`.
   *  After the `mutation` completes, a modification event is produced by the
   *  `eventLog` and can be used subsequently:
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
   *  def mutate(mut: ReactMutable)(mutation: T => Unit): Events.Subscription
   *  }}}
   *
   *  '''Note:'''
   *  No two events will ever be concurrently processed by different
   *  threads on the same event stream mutable,
   *  but an event that is propagated from within the `mutation` can trigger an
   *  event on `this`.
   *  The result is that `mutation` is invoked concurrently on the same thread.
   *  The following code is problematic has a feedback loop in the dataflow
   *  graph:
   *  
   *  {{{
   *  val emitter = new Events.Emitter[Int]
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
   *  Care must be taken to avoid `mutation` from emitting events that have
   *  feedback loops.
   *
   *  @tparam M         the type of the event stream mutable value
   *  @param mutable    the target mutable to be mutated with events from this
   *                    stream
   *  @param mutation   the function that modifies `mutable` given an event of
   *                    type `T`
   *  @return           a subscription used to cancel this mutation
   */
  def mutate[M <: ReactMutable](mutable: M)(mutation: T => Unit):
    Events.Subscription = {
    val rm = new Events.Mutate(self, mutable, mutation)
    rm.subscription = mutable.bindSubscription(self observe rm)
    rm
  }

  private def mutablesCompositeSubscription[M <: ReactMutable]
    (mutables: Seq[M], selfsub: Events.Subscription) = {
    for (m <- mutables) yield m.bindSubscription(selfsub)
  }

  /** Mutates multiple mutable event stream `m1`, `m2` and `mr` each time
   *  `this` event stream produces an event.
   *  
   *  This version of the `mutate` works on multiple event streams.
   *  
   *  @tparam M          the type of the mutable event stream
   *  @param m1          the first mutable
   *  @param m2          the second mutable
   *  @param mr          the rest of the mutables
   *  @param mutation    the function that modifies the mutables
   *  @return            a subscription used to cancel this mutation
   */
  def mutate[M <: ReactMutable](m1: M, m2: M, mr: M*)(mutation: T => Unit):
    Events.Subscription = {
    val mutables = Seq(m1, m2) ++ mr
    val rm = new Events.MutateMany(self, mutables, mutation)
    val selfsub = self observe rm
    val subs = mutablesCompositeSubscription(mutables, selfsub)
    rm.subscription = Events.CompositeSubscription(subs: _*)
    rm
  }

  /** Creates a new event stream that produces events from `this` event stream
   *  only after `that` produces an event.
   *
   *  After `that` emits some event, all events from `this` are produced on the
   *  resulting event stream.
   *  If `that` unreacts before an event is produced on `this`, the resulting
   *  event stream unreacts.
   *  If `this` unreacts, the resulting event stream unreacts.
   *
   *  @tparam S          the type of `that` event stream
   *  @param that        the event stream after whose first event the result can
   *                     start propagating events
   *  @return            a subscription and the resulting event stream that emits
   *                     only after `that` emits at least once.
   */
  def after[@spec(Int, Long, Double) S](that: Events[S]):
    Events[T] with Events.Subscription = {
    val ra = new Events.After(self, that)
    ra.selfSubscription = self observe ra.selfReactor
    ra.thatSubscription = that observe ra.thatReactor
    ra.subscription = Events.CompositeSubscription(
      ra.selfSubscription, ra.thatSubscription)
    ra
  }

  /** Creates a new event stream value that produces events from `this` event stream
   *  value until `that` produces an event.
   *  
   *  If `this` unreacts before `that` produces a value, the resulting event stream
   *  unreacts.
   *  Otherwise, the resulting event stream unreacts whenever `that` produces a
   *  value.
   *
   *  @tparam S         the type of `that` event stream
   *  @param that       the event stream until whose first event the result
   *                    propagates events
   *  @return           a subscription and the resulting event stream that emits
   *                    only until `that` emits
   */
  def until[@spec(Int, Long, Double) S](that: Events[S]):
    Events[T] with Events.Subscription = {
    val ru = new Events.Until(self, that)
    ru.selfSubscription = self observe ru.selfReactor
    ru.thatSubscription = that observe ru.thatReactor
    ru.subscription = Events.CompositeSubscription(
      ru.selfSubscription, ru.thatSubscription)
    ru
  }

  /** Creates an event stream that forwards an event from this event stream only once.
   *
   *  The resulting event stream emits only a single event produced by `this`
   *  event stream after `once` is called, and then unreacts.
   *
   *  {{{
   *  time ----------------->
   *  this --1-----2----3--->
   *  once      ---2|
   *  }}}
   *
   *  @return           a subscription and an event stream with the first event from
   *                    `this`
   */
  def once: Events[T] with Events.Subscription = {
    val ro = new Events.Once(self)
    ro.subscription = self observe ro
    ro
  }

  /** Filters events from `this` event stream value using a specified predicate `p`.
   *
   *  Only events from `this` for which `p` returns `true` are emitted on the
   *  resulting event stream.
   *
   *  @param p          the predicate used to filter events
   *  @return           a subscription and an event streams with the filtered events
   */
  def filter(p: T => Boolean): Events[T] with Events.Subscription = {
    val rf = new Events.Filter[T](self, p)
    rf.subscription = self observe rf
    rf
  }

  /** Filters events from `this` event stream and maps them in the same time.
   *
   *  The `collect` combinator uses a partial function `pf` to filter events
   *  from `this` event stream. Events for which the partial function is defined
   *  are mapped using the partial function, others are discarded.
   *
   *  '''Note:'''
   *  This combinator is defined only for event streams that contain reference
   *  events.
   *  You cannot call it for event streams whose events are primitive values, such
   *  as `Int`.
   *  This is because the `PartialFunction` class is not specialized.
   *
   *  @tparam S         the type of the mapped event stream
   *  @param pf         partial function used to filter and map events
   *  @param evidence   evidence that `T` is a reference type
   *  @return           a subscription and an event stream with the partially
   *                    mapped events
   */
  def collect[S <: AnyRef](pf: PartialFunction[T, S])
    (implicit evidence: T <:< AnyRef):
    Events[S] with Events.Subscription = {
    val cf = new Events.Collect[T, S](self, pf)
    cf.subscription = self observe cf
    cf
  }

  /** Returns a new event stream that maps events from `this` event stream using the
   *  mapping function `f`.
   *
   *  {{{
   *  time    --------------------->
   *  this    --e1------e2------|
   *  mapped  --f(e1)---f(e2)---|
   *  }}}
   *
   *  @tparam S         the type of the mapped events
   *  @param f          the mapping function
   *  @return           a subscription and event stream value with the mapped events
   */
  def map[@spec(Int, Long, Double) S](f: T => S):
    Events[S] with Events.Subscription = {
    val rm = new Events.Map[T, S](self, f)
    rm.subscription = self observe rm
    rm
  }

  /** Returns a new event stream that forwards the events from `this` event stream as
   *  long as they satisfy the predicate `p`.
   *
   *  After an event that does not specify the predicate occurs, the resulting
   *  event stream unreacts.
   *
   *  If the predicate throws an exception, the exceptions is propagated, and the
   *  resulting event stream unreacts.
   *
   *  {{{
   *  time             ------------------------>
   *  this             -0---1--2--3-4--1-5--2-->
   *  takeWhile(_ < 4)     -1--2--3-|---------->
   *  }}}
   *
   *  @param p          the predicate that specifies whether to take the element
   *  @return           a subscription and event stream value with the forwarded events
   */
  def takeWhile(p: T => Boolean): Events[T] with Events.Subscription = {
    val rm = new Events.TakeWhile[T](self, p)
    rm.subscription = self observe rm
    rm
  }

  /** Splits the primitive value events from this event stream into an event stream
   *  value pair.
   *
   *  Events in this event stream must be primitive values.
   *
   *  @tparam P         the type of the first value in the event stream pair
   *  @tparam Q         the type of the second value in the event stream pair
   *  @param pf         mapping function from events in this event stream to the
   *                    first part of the pair
   *  @param qf         mapping function from events in this event stream to the
   *                    second part of the pair
   *  @param e          evidence that events in this event stream are values
   *  @return           event stream value pair
   */
  def rvsplit[
    @spec(Int, Long, Double) P <: AnyVal,
    @spec(Int, Long, Double) Q <: AnyVal
  ](pf: T => P)(qf: T => Q)(implicit e: T <:< AnyVal): RValPair[P, Q] = {
    val e = new RValPair.Emitter[P, Q]
    e.subscription = this.observe(new Reactor[T] {
      def react(x: T) {
        var p = null.asInstanceOf[P]
        var q = null.asInstanceOf[Q]
        try {
          p = pf(x)
          q = qf(x)
        } catch {
          case t if isNonLethal(t) =>
            except(t)
            return
        }
        e.react(p, q)
      }
      def except(t: Throwable) = e.except(t)
      def unreact() = e.unreact()
    })
    e
  }

  /** Splits the object events from this event stream into an event stream value pair.
   *
   *  Events in this event stream must be objects.
   *
   *  @tparam P         the type of the first value in the event stream pair
   *  @tparam Q         the type of the second value in the event stream pair
   *  @param pf         mapping function from events in this event stream to the
   *                    first part of the pair
   *  @param qf         mapping function from events in this event stream to the
   *                    second part of the pair
   *  @param ev         evidence that events in this event stream are values
   *  @return           event stream value pair
   */
  def rvsplit[
    @spec(Int, Long, Double) P <: AnyVal,
    @spec(Int, Long, Double) Q <: AnyVal
  ](pf: RVFun[T, P])(qf: RVFun[T, Q])(implicit ev: T <:< AnyRef):
    RValPair[P, Q] = {
    val e = new RValPair.Emitter[P, Q]
    e.subscription = this.observe(new Reactor[T] {
      def react(x: T) {
        var p = null.asInstanceOf[P]
        var q = null.asInstanceOf[Q]
        try {
          p = pf(x)
          q = qf(x)
        } catch {
          case t if isNonLethal(t) =>
            except(t)
            return
        }
        e.react(p, q)
      }
      def except(t: Throwable) = e.except(t)
      def unreact() = e.unreact()
    })
    e
  }

  /** Splits the events from this event stream into a event stream pair.
   *
   *  '''Note:'''
   *  This event stream needs to contain object events.
   *
   *  @tparam P         the type of the first value in the event stream pair
   *  @tparam Q         the type of the second value in the event stream pair
   *  @param pf         mapping function from events in this event stream to the
   *                    first part of the pair
   *  @param qf         mapping function from events in this event stream to the
   *                    second part of the pair
   *  @return           event stream pair
   */
  def split[
    @spec(Int, Long, Double) P <: AnyVal,
    Q <: AnyRef
  ](pf: RVFun[T, P])(qf: T => Q)(implicit ev: T <:< AnyRef): RPair[P, Q] = {
    val e = new RPair.Emitter[P, Q]
    e.subscription = this.observe(new Reactor[T] {
      def react(x: T) {
        var p = null.asInstanceOf[P]
        var q = null.asInstanceOf[Q]
        try {
          p = pf(x)
          q = qf(x)
        } catch {
          case t if isNonLethal(t) =>
            except(t)
            return
        }
        e.react(p, q)
      }
      def except(t: Throwable) = e.except(t)
      def unreact() = e.unreact()
    })
    e
  }

  /** Splits the events from this event stream into a event stream pair.
   *
   *  '''Note:'''
   *  This event stream needs to contain object events.
   *
   *  @tparam P         the type of the first value in the event stream pair
   *  @tparam Q         the type of the second value in the event stream pair
   *  @param pf         mapping function from events in this event stream to the
   *                    first part of the pair
   *  @param qf         mapping function from events in this event stream to the
   *                    second part of the pair
   *  @param e          evidence that events in this event stream are values
   *  @return           event stream pair
   */
  def split[P <: AnyRef, Q <: AnyRef](pf: T => P)(qf: T => Q)
    (implicit ev: T <:< AnyRef): RPair[P, Q] = {
    val e = new RPair.Emitter[P, Q]
    e.subscription = this.observe(new Reactor[T] {
      def react(x: T) {
        var p = null.asInstanceOf[P]
        var q = null.asInstanceOf[Q]
        try {
          p = pf(x)
          q = qf(x)
        } catch {
          case t if isNonLethal(t) =>
            except(t)
            return
        }
        e.react(p, q)
      }
      def except(t: Throwable) = e.except(t)
      def unreact() = e.unreact()
    })
    e
  }

  /* higher-order combinators */

  /** Returns events from the last event stream that `this` emitted as an
   *  event of its own, in effect multiplexing the nested reactives.
   *
   *  The resulting event stream only emits events from the event stream last
   *  emitted by `this`, the preceding event streams are ignored.
   *
   *  This combinator is only available if this event stream emits events
   *  that are themselves event streams.
   *
   *  Example:
   *
   *  {{{
   *  val currentEvents = new Events.Emitter[Events[Int]]
   *  val e1 = new Events.Emitter[Int]
   *  val e2 = new Events.Emitter[Int]
   *  val currentEvent = currentEvents.mux()
   *  val prints = currentEvent.onEvent(println) 
   *  
   *  currentEvents += e1
   *  e2 += 1 // nothing is printed
   *  e1 += 2 // 2 is printed
   *  currentEvents += e2
   *  e2 += 6 // 6 is printed
   *  e1 += 7 // nothing is printed
   *  }}}
   *
   *  Shown on the diagram:
   *
   *  {{{
   *  time            ------------------->
   *  currentEvents   --e1------e2------->
   *  e1              --------2----6----->
   *  e2              -----1----------7-->
   *  currentEvent    --------2----6----->
   *  }}}
   *
   *  '''Use case:'''
   *
   *  {{{
   *  def mux[S](): Events[S]
   *  }}}
   *
   *  @tparam S          the type of the events in the nested event stream
   *  @param evidence    an implicit evidence that `this` event stream is nested --
   *                     it emits events of type `T` that is actually a
   *                     `Events[S]`
   *  @return            event stream of events from the event stream last emitted by
   *                     `this`
   */
  def mux[@spec(Int, Long, Double) S]()
    (implicit evidence: T <:< Events[S]): Events[S] = {
    new Events.Mux[T, S](this, evidence)
  }

  /** Invokes a side-effect every time an event gets produced.
   *  
   *  @param f          an effect to invoke when an event is produced
   *  @return           an event stream with the same events as this event stream
   */
  def effect(f: T => Unit): Events[T] = self.map({ x => f(x); x })

  /** Returns a new event stream containing the same sequence of events as this event
   *  stream, but also contains the specified subscription.
   *
   *  @param sub        the new subscription
   *  @return           new event stream and subscription
   */
  def withSubscription(sub: Events.Subscription): Events[T] with Events.Subscription = {
    new Events.WithSubscription(this, sub)
  }

  /** Returns a new event stream that emits an event when this event stream unreacts.
   *
   *  After the current event stream unreacts, the result event stream first emits an
   *  event of type `Unit`, and then unreacts itself.
   *
   *  Exceptions from this event stream are propagated until the resulting event stream
   *  unreacts -- after that, `this` event stream is not allowed to produce exceptions.
   *
   *  Shown on the diagram:
   *
   *  {{{
   *  time            ------------------->
   *  this            --1--2-----3-|----->
   *  currentEvent    -------------()-|-->
   *  }}}
   *
   *  @return           the unreaction event stream and subscription
   */
  def unreacted: Events[Unit] with Events.Subscription = {
    new Events.Unreacted(this)
  }

}


/** Contains useful `Events` implementations and factory methods.
 */
object Events {

  implicit def events2ops[@spec(Int, Long, Double) T](self: Events[T]) =
    new EventsOps(self)

  class EventsOps[@spec(Int, Long, Double) T](val self: Events[T]) {
    /** Creates an `Ivar` event stream value, completed with the first event from
     *  this event stream.
     *
     *  After the `Ivar` is assigned, all subsequent events are ignored.
     *  If the `self` event stream is unreacted before any event arrives, the
     *  `Ivar` is closed.
     *
     *  @return          an `Ivar` with the first event from this event stream
     */
    def ivar: Ivar[T] = {
      val iv = new Ivar[T]
      val r = new Events.IvarAssignReactor(iv)
      r.subscription = self.observe(r)
      iv
    }

    /** Accumulates all the events from this event stream into a new container of the
     *  specified type.
     *
     *  The events are added to the specified container until this event stream
     *  unreacts.
     *  It is the client's responsibility to ensure that the event stream is not
     *  unbounded.
     *
     *  @tparam That     the type of the reactive container
     *  @param factory   the factory of the builder objects for the specified container
     *  @result          the reactive container with all the emitted events
     */
    def to[That <: RContainer[T]](implicit factory: RBuilder.Factory[T, That]): That = {
      val builder = factory()
      val result = builder.container
      result.subscriptions += self.mutate(builder) { builder += _ }
      result
    }

    /** Given an initial event `init`, converts this event stream into a `Signal`.
     *
     *  The resulting signal initially contains the event `init`,
     *  and subsequently any event that the `this` event stream produces.
     *
     *  @param init      an initial value for the signal
     *  @return          the signal version of the current event stream
     */
    def signal(init: T) = self.scanPast(init) {
      (cached, value) => value
    }

    /** If the current event stream is a signal already this method downcasts it,
     *  otherwise it lifts it into a signal with the initial value `init`.
     *
     *  @param init      optional value to use when converting the event stream to a
     *                   signal
     *  @return          the signal version of the current event stream
     */
    def asSignalOrElse(init: T) = self match {
      case s: Signal[T] => s
      case _ => signal(init)
    }

    /** Downcasts this event stream into a signal.
     *
     *  Throws an exception if the current event stream is not a signal.
     *
     *  @return          the signal version of the current event stream
     */
    def asSignal = this match {
      case s: Signal[T] => s
      case _ => throw new UnsupportedOperationException("This is not a signal.")
    }

    /** Pipes the events from this event stream to the specified channel.
     *  
     *  The call `r.pipe(c)` is equivalent to the following:
     *  {{{
     *  c.attach(r)
     *  }}}
     */
    def pipe(c: Channel[T]): Unit = c.attach(self)

    /** Pipes the events from this event stream to the specified emitter.
     *
     *  @param em        emitter to forward the events to
     */
    def pipe(em: Events.Emitter[T]): Events.Subscription =
      self.foreach(em react _)

    /** Creates a union of `this` and `that` event stream.
     *  
     *  The resulting event stream emits events from both `this` and `that`
     *  event stream.
     *  It unreacts when both `this` and `that` event stream unreact.
     *
     *  @param that      another event stream for the union
     *  @return          a subscription and the event stream with unified
     *                   events from `this` and `that`
     */
    def union(that: Events[T]): Events[T] with Events.Subscription = {
      val ru = new Events.Union(self, that)
      ru.selfSubscription = self observe ru.selfReactor
      ru.thatSubscription = that observe ru.thatReactor
      ru.subscription = Events.CompositeSubscription(
        ru.selfSubscription, ru.thatSubscription)
      ru
    }
  
    /** Creates a concatenation of `this` and `that` event stream.
     *
     *  The resulting event stream produces all the events from `this`
     *  event stream until `this` unreacts, and then outputs all the events from
     *  `that` that happened before and after `this` unreacted.
     *  To do this, this operation potentially caches all the events from
     *  `that`.
     *  When `that` unreacts, the resulting event stream unreacts.
     *
     *  '''Use case:'''
     *
     *  {{{
     *  def concat(that: Events[T]): Events[T]
     *  }}}
     *
     *  @param that      another event stream for the concatenation
     *  @note This operation potentially caches events from `that`.
     *  Unless certain that `this` eventually unreacts, `concat` should not be
     *  used.
     *  To enforce this, clients must import the `CanBeBuffered` evidence
     *  explicitly into the scope in which they call `concat`.
     *  
     *  @param a         evidence that arrays can be created for the type `T`
     *  @param b         evidence that the client allows events from `that` to
     *                   be buffered
     *  @return          a subscription and event stream that concatenates
     *                   events from `this` and `that`
     */
    def concat(that: Events[T])(implicit a: Arrayable[T], b: CanBeBuffered):
      Events[T] with Events.Subscription = {
      val rc = new Events.Concat(self, that, a)
      rc.selfSubscription = self observe rc.selfReactor
      rc.thatSubscription = that observe rc.thatReactor
      rc.subscription = Events.CompositeSubscription(
        rc.selfSubscription, rc.thatSubscription)
      rc
    }
  
    /** Syncs the arrival of events from `this` and `that` event stream.
     *  
     *  Ensures that pairs of events from this event stream and that event stream
     *  are emitted together.
     *  If the events produced in time by `this` and `that`, the sync will be as
     *  follows:
     *
     *  {{{
     *  time   --------------------------->
     *  this   ----1---------2-------4---->
     *  that   --1-----2--3--------------->
     *  sync   ----1,1-------2,2-----4,3-->
     *  }}}
     *
     *  Pairs of events produced from `this` and `that` are then transformed
     *  using specified function `f`.
     *  For example, clients that want to output tuples do:
     *
     *  {{{
     *  val synced = (a sync b) { (a, b) => (a, b) }
     *  }}}
     *
     *  Clients that, for example, want to create differences in pairs of events
     *  do:
     *
     *  {{{
     *  val diffs = (a sync b)(_ - _)
     *  }}}
     *
     *  The resulting event stream unreacts either when
     *  `this` unreacts and there are no more buffered events from this,
     *  or when `that` unreacts and there are no more buffered events from
     *  `that`.
     *
     *  '''Use case:'''
     *
     *  {{{
     *  def sync[S, R](that: Events[S])(f: (T, S) => R): Events[R]
     *  }}}
     *
     *  @note This operation potentially caches events from `this` and `that`.
     *  Unless certain that both `this` produces a bounded number of events
     *  before the `that` produces an event, and vice versa, this operation
     *  should not be called.
     *  To enforce this, clients must import the `CanBeBuffered` evidence
     *  explicitly into the scope in which they call `sync`.
     *
     *  @tparam S         the type of the events in `that` event stream
     *  @tparam R         the type of the events in the resulting event stream
     *  @param that       the event stream to sync with
     *  @param f          the mapping function for the pair of events
     *  @param at         evidence that arrays can be created for the type `T`
     *  @param as         evidence that arrays can be created for the type `S`
     *  @param b          evidence that the client allows events to be buffered
     *  @return           a subscription and the event stream with the resulting
     *                    events
     */
    def sync[@spec(Int, Long, Double) S, @spec(Int, Long, Double) R]
      (that: Events[S])(f: (T, S) => R)
      (implicit at: Arrayable[T], as: Arrayable[S], b: CanBeBuffered):
      Events[R] with Events.Subscription = {
      val rs = new Events.Sync(self, that, f, at, as)
      rs.selfSubscription = self observe rs.selfReactor
      rs.thatSubscription = that observe rs.thatReactor
      rs.subscription = Events.CompositeSubscription(
        rs.selfSubscription, rs.thatSubscription)
      rs
    }

    /* higher-order combinators */

    /** Unifies the events produced by all the event streams emitted by `this`.
     *
     *  This operation is only available for event stream values that emit
     *  other event streams as events.
     *  The resulting event stream unifies events of all the event streams emitted by
     *  `this`.
     *  Once `this` and all the event streams emitted by `this` unreact, the
     *  resulting event stream terminates.
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
     *  def union[S](): Events[S]
     *  }}}
     *
     *  @tparam S         the type of the events in event streams emitted by `this`
     *  @param evidence   evidence that events of type `T` produced by `this`
     *                    are actually event stream values of type `S`
     *  @return           a subscription and the event stream with the union of all
     *                    the events
     *  
     */
    def union[@spec(Int, Long, Double) S]()
      (implicit evidence: T <:< Events[S]):
      Events[S] with Events.Subscription = {
      new Events.PostfixUnion[T, S](self, evidence)
    }

    /** Concatenates the events produced by all the event streams emitted by `this`.
     *
     *  This operation is only available for event stream values that emit
     *  other event streams as events.
     *  Once `this` and all the event streams unreact, this event stream unreacts.
     *
     *  '''Use case:'''
     *
     *  {{{
     *  def concat[S](): Events[S]
     *  }}}
     *
     *  @note This operation potentially buffers events from the nested
     *  event streams.
     *  Unless each event stream emitted by `this` is known to unreact eventually,
     *  this operation should not be called.
     *  To enforce this, clients are required to import the `CanBeBuffered`
     *  evidence explicitly into the scope in which they call `concat`.
     *  
     *  @tparam S         the type of the events in event streams emitted by `this`
     *  @param evidence   evidence that events of type `T` produced by `this`
     *                    are actually event stream values of type `S`
     *  @param a          evidence that arrays can be created for type `S`
     *  @param b          evidence that buffering events is allowed
     *  @return           a subscription and the event stream that concatenates all
     *                    the events
     */
    def concat[@spec(Int, Long, Double) S]()
      (implicit evidence: T <:< Events[S], a: Arrayable[S], b: CanBeBuffered):
      Events[S] with Events.Subscription = {
      val pc = new Events.PostfixConcat[T, S](self, evidence)
      pc.subscription = self observe pc
      pc
    }
  }

  private[reactive] class IvarAssignReactor[@spec(Int, Long, Double) T]
    (val iv: Ivar[T])
  extends Reactor[T] {
    var subscription = Subscription.empty
    def react(value: T) {
      if (iv.isUnassigned) {
        try iv := value
        finally subscription.unsubscribe()
      }
    }
    def except(t: Throwable) {}
    def unreact() {
      if (iv.isUnassigned) {
        try iv.unreact()
        finally subscription.unsubscribe()
      }
    }
  }

  private[reactive] class Foreach[@spec(Int, Long, Double) T]
    (val self: Events[T], val f: T => Unit)
  extends Events.Default[Unit] with Reactor[T]
  with Events.ProxySubscription {
    def react(value: T) {
      try {
        f(value)
      } catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          return
      }
      reactAll(())
    }
    def except(t: Throwable) {
      exceptAll(t)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactive] class Handle[@spec(Int, Long, Double) T]
    (val self: Events[T], val f: Throwable => Unit)
  extends Events.Default[Unit] with Reactor[T]
  with Events.ProxySubscription {
    def react(value: T) {}
    def except(t: Throwable) {
      try f(t)
      catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          return
      }
      reactAll(())
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactive] class Ultimately[@spec(Int, Long, Double) T]
    (val self: Events[T], val body: () => Unit)
  extends Events.Default[Unit] with Reactor[T]
  with Events.ProxySubscription {
    def react(value: T) {}
    def except(t: Throwable) {
      exceptAll(t)
    }
    def unreact() {
      try {
        body()
      } catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          unreactAll()
          return
      }
      reactAll(())
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactive] class Recover[U]
    (val self: Events[U], val pf: PartialFunction[Throwable, U])
    (implicit evid: U <:< AnyRef)
  extends Events.Default[U] with Reactor[U]
  with Events.ProxySubscription {
    def react(value: U) {
      reactAll(value)
    }
    def except(t: Throwable) {
      val isDefined = try {
        pf.isDefinedAt(t)
      } catch {
        case other if isNonLethal(other) =>
          exceptAll(other)
          return
      }
      if (!isDefined) exceptAll(t)
      else {
        val event = try {
          pf(t)
        } catch {
          case other if isNonLethal(other) =>
            exceptAll(other)
            return
        }
        reactAll(event)
      }
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactive] class ScanPast
    [@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Events[T], val z: S, val op: (S, T) => S)
  extends Signal.Default[S] with Reactor[T] with Events.ProxySubscription {
    private var cached: S = _
    def init(z: S) {
      cached = z
    }
    init(z)
    def apply() = cached
    def react(value: T) {
      try {
        cached = op(cached, value)
      } catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          return
      }
      reactAll(cached)
    }
    def except(t: Throwable) {
      exceptAll(t)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactive] class Mutate[@spec(Int, Long, Double) T, M <: ReactMutable]
    (val self: Events[T], val mutable: M, val mutation: T => Unit)
  extends Reactor[T] with Events.ProxySubscription {
    def react(value: T) = {
      try {
        mutation(value)
      } catch {
        case t if isNonLethal(t) =>
          except(t)
          // note: still need to mutate, just in case
      }
      mutable.mutation()
    }
    def except(t: Throwable) {
      mutable.exception(t)
    }
    def unreact() {}
    var subscription = Subscription.empty
  }

  private[reactive] class MutateMany
    [@spec(Int, Long, Double) T, M <: ReactMutable]
    (val self: Events[T], val mutables: Seq[M], val mutation: T => Unit)
  extends Reactor[T] with Events.ProxySubscription {
    def react(value: T) = {
      try {
        mutation(value)
      } catch {
        case t if isNonLethal(t) =>
          exception(t)
          // note: still need to mutate, just in case
      }
      for (m <- mutables) m.mutation()
    }
    def except(t: Throwable) {
      for (m <- mutables) m.exception(t)
    }
    def unreact() {}
    var subscription = Subscription.empty
  }

  private[reactive] class After
    [@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Events[T], val that: Events[S])
  extends Events.Default[T] with Events.ProxySubscription {
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
      def except(t: Throwable) {
        exceptAll(t)
      }
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[S] {
      def react(value: S) {
        started = true
      }
      def except(t: Throwable) {
        if (!started) exceptAll(t)
      }
      def unreact() = if (!started) unreactBoth()
    }
    var selfSubscription = Subscription.empty
    var thatSubscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactive] class Until
    [@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Events[T], val that: Events[S])
  extends Events.Default[T] with Events.ProxySubscription {
    var live = true
    def unreactBoth() = if (live) {
      live = false
      unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) = if (live) reactAll(value)
      def except(t: Throwable) = if (live) exceptAll(t)
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[S] {
      def react(value: S) = unreactBoth()
      def except(t: Throwable) = if (live) exceptAll(t)
      def unreact() {}
    }
    var selfSubscription: Events.Subscription = Subscription.empty
    var thatSubscription: Events.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactive] class Union[@spec(Int, Long, Double) T]
    (val self: Events[T], val that: Events[T])
  extends Events.Default[T] with Events.ProxySubscription {
    var live = 2
    def unreactBoth() = {
      live -= 1
      if (live == 0) unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) = reactAll(value)
      def except(t: Throwable) = exceptAll(t)
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[T] {
      def react(value: T) = reactAll(value)
      def except(t: Throwable) = exceptAll(t)
      def unreact() = unreactBoth()
    }
    var selfSubscription: Events.Subscription = Subscription.empty
    var thatSubscription: Events.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactive] class Concat[@spec(Int, Long, Double) T]
    (val self: Events[T], val that: Events[T], val a: Arrayable[T])
  extends Events.Default[T] with Events.ProxySubscription {
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
      def except(t: Throwable) = exceptAll(t)
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
      def except(t: Throwable) = exceptAll(t)
      def unreact() {
        thatLive = false
        unreactBoth()
      }
    }
    var selfSubscription: Events.Subscription = Subscription.empty
    var thatSubscription: Events.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactive] class Sync[
    @spec(Int, Long, Double) T,
    @spec(Int, Long, Double) S,
    @spec(Int, Long, Double) R
  ](
    val self: Events[T], val that: Events[S], val f: (T, S) => R,
    val at: Arrayable[T], val as: Arrayable[S]
  ) extends Events.Default[R] with Events.ProxySubscription {
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
          val event = try {
            f(tvalue, svalue)
          } catch {
            case t if isNonLethal(t) =>
              exceptAll(t)
              return
          }
          reactAll(event)
        }
      }
      def except(t: Throwable) {
        exceptAll(t)
      }
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[S] {
      def react(svalue: S) {
        if (tbuffer.isEmpty) sbuffer += svalue
        else {
          val tvalue = tbuffer.dequeue()
          val event = try {
            f(tvalue, svalue)
          } catch {
            case t if isNonLethal(t) =>
              exceptAll(t)
              return
          }
          reactAll(event)
        }
      }
      def except(t: Throwable) {
        exceptAll(t)
      }
      def unreact() = unreactBoth()
    }
    var selfSubscription: Events.Subscription = Subscription.empty
    var thatSubscription: Events.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  private[reactive] class Filter[@spec(Int, Long, Double) T]
    (val self: Events[T], val p: T => Boolean)
  extends Events.Default[T] with Reactor[T] with Events.ProxySubscription {
    def react(value: T) {
      val ok = try {
        p(value)
      } catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          return
      }
      if (ok) reactAll(value)
    }
    def except(t: Throwable) {
      exceptAll(t)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactive] class Collect[T, S <: AnyRef]
    (val self: Events[T], val pf: PartialFunction[T, S])
  extends Events.Default[S] with Reactor[T] with Events.ProxySubscription {
    def react(value: T) {
      val isDefined = try {
        pf.isDefinedAt(value)
      } catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          return
      }
      if (isDefined) {
        val event = try {
          pf(value)
        } catch {
          case t if isNonLethal(t) =>
            exceptAll(t)
            return
        }
        reactAll(event)
      }
    }
    def except(t: Throwable) {
      exceptAll(t)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactive] class Map
    [@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Events[T], val f: T => S)
  extends Events.Default[S] with Reactor[T] with Events.ProxySubscription {
    def react(value: T) {
      val event = try {
        f(value)
      } catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          return
      }
      reactAll(event)
    }
    def except(t: Throwable) {
      exceptAll(t)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactive] class TakeWhile
    [@spec(Int, Long, Double) T]
    (val self: Events[T], val p: T => Boolean)
  extends Events.Default[T] with Reactor[T] with Events.ProxySubscription {
    def react(value: T) {
      val shouldForward = try {
        p(value)
      } catch {
        case t if isNonLethal(t) =>
          unsubscribe()
          exceptAll(t)
          unreactAll()
          return
      }
      if (shouldForward) reactAll(value)
      else {
        unsubscribe()
        unreactAll()
      }
    }
    def except(t: Throwable) {
      exceptAll(t)
    }
    def unreact() {
      unsubscribe()
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  private[reactive] class Once[@spec(Int, Long, Double) T]
    (val self: Events[T])
  extends Events.Default[T] with Reactor[T] with Events.ProxySubscription {
    private var forwarded = false
    def react(value: T) {
      if (!forwarded) {
        reactAll(value)
        unreact()
      }
    }
    def except(t: Throwable) {
      if (!forwarded) {
        exceptAll(t)
      }
    }
    def unreact() {
      if (!forwarded) {
        forwarded = true
        unreactAll()
        subscription.unsubscribe()
      }
    }
    var subscription = Subscription.empty
  }

  private[reactive] class WithSubscription[@spec(Int, Long, Double) T]
    (val self: Events[T], val sub: Subscription)
  extends Events.Default[T] with Reactor[T] with Events.ProxySubscription {
    def react(value: T) = reactAll(value)
    def except(t: Throwable) = exceptAll(t)
    def unreact() = unreactAll()
    var subscription = Subscription.empty
    def init(dummy: Events[T]) {
      subscription = CompositeSubscription(
        sub,
        self.observe(this)
      )
    }
    init(this)
  }

  private[reactive] class Unreacted[@spec(Int, Long, Double) T](val self: Events[T])
  extends Events.Default[Unit] with Reactor[T] with Events.ProxySubscription {
    def react(value: T) = {}
    def except(t: Throwable) = exceptAll(t)
    def unreact() {
      reactAll(())
      unreactAll()
    }
    var subscription = Subscription.empty
    def init(dummy: Events[Unit]) {
      subscription = self.observe(this)
    }
    init(this)
  }

  private[reactive] class Mux[T, @spec(Int, Long, Double) S]
    (val self: Events[T], val evidence: T <:< Events[S])
  extends Events.Default[S] with Reactor[T] with Events.ProxySubscription {
    muxed =>
    private[reactive] var currentSubscription: Subscription = null
    private[reactive] var terminated = false
    def newReactor: Reactor[S] = new Reactor[S] {
      def react(value: S) = reactAll(value)
      def except(t: Throwable) = {
        exceptAll(t)
      }
      def unreact() {
        currentSubscription = Subscription.empty
        checkUnreact()
      }
    }
    def checkUnreact() =
      if (terminated && currentSubscription == Subscription.empty) unreactAll()
    def react(value: T) {
      val nextEvents = try {
        evidence(value)
      } catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          return
      }
      currentSubscription.unsubscribe()
      currentSubscription = nextEvents observe newReactor
    }
    def except(t: Throwable) {
      exceptAll(t)
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
    def init(e: T <:< Events[S]) {
      currentSubscription = Subscription.empty
      subscription = self observe this
    }
    init(evidence)
  }

  private[reactive] class PostfixUnion[T, @spec(Int, Long, Double) S]
    (val self: Events[T], val evidence: T <:< Events[S])
  extends Events.Default[S] with Reactor[T] with Events.ProxySubscription {
    union =>
    private[reactive] var subscriptions =
      mutable.Map[Events[S], Subscription]()
    private[reactive] var terminated = false
    def checkUnreact() = if (terminated && subscriptions.isEmpty) unreactAll()
    def newReactor(r: Events[S]) = new Reactor[S] {
      def react(value: S) = reactAll(value)
      def except(t: Throwable) {
        exceptAll(t)
      }
      def unreact() = {
        subscriptions.remove(r)
        checkUnreact()
      }
    }
    def react(value: T) {
      val nextEvents = try {
        evidence(value)
      } catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          return
      }
      if (!subscriptions.contains(nextEvents)) {
        val sub = nextEvents observe newReactor(nextEvents)
        subscriptions(nextEvents) = sub
      }
    }
    def except(t: Throwable) {
      exceptAll(t)
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
    val subscription = self observe this
  }

  private[reactive] final class ConcatEntry[S](
    var subscription: Subscription,
    var buffer: UnrolledBuffer[S],
    var live: Boolean) {
    def ready = buffer == null
  }

  private[reactive] class PostfixConcat[T, @spec(Int, Long, Double) S]
    (val self: Events[T], val evidence: T <:< Events[S])
    (implicit val arrayable: Arrayable[S])
  extends Events.Default[S] with Reactor[T] with Events.ProxySubscription {
    union =>
    private[reactive] var subscriptions = new UnrolledBuffer[ConcatEntry[S]]()
    private[reactive] var terminated = false
    private def checkUnreact() =
      if (terminated && subscriptions.isEmpty) unreactAll()
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
      def except(t: Throwable) {
        exceptAll(t)
      }
      def unreact() {
        if (entry.ready) {
          subscriptions.dequeue()
          moveToNext()
        } else entry.live = false
      }
    }
    def react(value: T) {
      val nextEvents = try {
        evidence(value)
      } catch {
        case t if isNonLethal(t) =>
          exceptAll(t)
          return
      }
      val entry = new ConcatEntry(null, new UnrolledBuffer[S], true)
      entry.subscription = nextEvents observe newReactor(entry)
      subscriptions.enqueue(entry)
      if (!subscriptions.head.ready) moveToNext()
    }
    def except(t: Throwable) {
      exceptAll(t)
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

  /** A base trait for event streams that never emit events.
   *
   *  @tparam T         type of events never emitted by this event stream
   */
  trait Never[@spec(Int, Long, Double) T]
  extends Events[T] {
    def hasSubscriptions = false
    def observe(reactor: Reactor[T]) = {
      reactor.unreact()
      Subscription.empty
    }
    def reactAll(value: T) {}
    def exceptAll(t: Throwable) {}
    def unreactAll() {}
  }

  private object NeverImpl extends Never[Nothing]

  /** A event stream that never emits events.
   * 
   *  @tparam T         type of events in this event stream
   */
  def Never[T] = NeverImpl.asInstanceOf[Events[T]]

  // TODO Amb

  /** The proxy event stream that emits events of its underlying event stream.
   *
   *  @tparam T         type of the proxy signal
   */
  trait Proxy[@spec(Int, Long, Double) T]
  extends Events[T] {
    val underlying: Events[T]
    def hasSubscriptions = underlying.hasSubscriptions
    def observe(r: Reactor[T]) = underlying.observe(r)
  }

  /** A subscription to a certain kind of event,
   *  event processing or computation in general.
   *  Calling `unsubscribe` on the subscription
   *  causes the events to no longer be propagated
   *  to this particular subscription or some computation to cease.
   *
   *  Unsubscribing is idempotent -- calling `unsubscribe` second time does
   *  nothing.
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
    /** Invokes the specified `onUnsubscribe` block when `unsubscribe` is
     *  called.
     *
     *  @param onUnsubscribe     code to execute when `unsubscribe` is called
     */
    def apply(onUnsubscribe: =>Unit) = new Subscription {
      def unsubscribe() = onUnsubscribe
    }

    def registerLeakySubscription(canLeak: CanLeak, s: Subscription) {
      canLeak.leakySubscriptions += s
    }

    def unregisterLeakySubscription(canLeak: CanLeak, s: Subscription) {
      canLeak.leakySubscriptions -= s
    }

    def liftSubscription(canLeak: CanLeak, s: Events.Subscription) = {
      Events.Subscription {
        unregisterLeakySubscription(canLeak, s)
        s.unsubscribe()
      }
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
  def CompositeSubscription(ss: Subscription*): Subscription =
    new Subscription {
      def unsubscribe() {
        for (s <- ss) s.unsubscribe()
      }
    }

  private val bufferUpperBound = 8
  private val hashTableLowerBound = 5

  /** The default implementation of a event stream value.
   *
   *  Keeps an optimized weak collection of weak references to subscribers.
   *  References to subscribers that are no longer reachable in the application
   *  will be removed eventually.
   *
   *  @tparam T       type of the events in this event stream value
   */
  trait Default[@spec(Int, Long, Double) T] extends Events[T] {
    private[reactive] var demux: AnyRef = null
    private[reactive] var eventsUnreacted: Boolean = false
    def observe(reactor: Reactor[T]) = {
      if (eventsUnreacted) {
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
    def exceptAll(t: Throwable) {
      demux match {
        case null =>
          // no need to inform anybody
        case w: WeakRef[Reactor[T] @unchecked] =>
          val r = w.get
          if (r != null) r.except(t)
          else demux = null
        case wb: WeakBuffer[Reactor[T] @unchecked] =>
          bufferExceptAll(wb, t)
        case wht: WeakHashTable[Reactor[T] @unchecked] =>
          tableExceptAll(wht, t)
      }
    }
    def unreactAll() {
      eventsUnreacted = true
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
    private def bufferExceptAll(wb: WeakBuffer[Reactor[T]], t: Throwable) {
      val array = wb.array
      var until = wb.size
      var i = 0
      while (i < until) {
        val ref = array(i)
        val r = ref.get
        if (r ne null) {
          r.except(t)
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
    private def cleanHashTable(wht: WeakHashTable[Reactor[T]]) {
      val table = wht.table
      var i = 0
      while (i < table.length) {
        val ref = table(i)
        if (ref ne null) {
          val r = ref.get
          if (r eq null) wht.removeEntryAt(i, null)
        }
        i += 1
      }
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
        }
        i += 1
      }
      cleanHashTable(wht)
      checkHashTable(wht)
    }
    private def tableExceptAll(wht: WeakHashTable[Reactor[T]], t: Throwable) {
      val table = wht.table
      var i = 0
      while (i < table.length) {
        val ref = table(i)
        if (ref ne null) {
          val r = ref.get
          if (r ne null) r.except(t)
        }
        i += 1
      }
      cleanHashTable(wht)
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
        }
        i += 1
      }
      cleanHashTable(wht)
      checkHashTable(wht)
    }
    def hasSubscriptions: Boolean = demux != null
    def onSubscriptionChange() {}
  }

  /** An event stream value that can programatically emit events.
   *
   *  Events are emitted to the event stream value by calling the `+=` method.
   *  The emitter can be closed by calling the `close` method --
   *  after this no more events will be accepted through `+=`.
   *
   *  Example:
   *  
   *  {{{
   *  val emitter = new Events.Emitter[Int]
   *  val prints = emitter.onEvent(println)
   *  emitter += 1
   *  emitter += 2
   *  }}}
   *
   *  @tparam       the type of events that this emitter can emit
   */
  class Emitter[@spec(Int, Long, Double) T]
  extends Events[T] with Default[T] with EventSource with Reactor[T] {
    private var live = true
    def react(value: T) {
      if (live) reactAll(value)
    }
    def except(t: Throwable) {
      if (live) exceptAll(t)
    }
    def unreact(): Unit = if (live) {
      live = false
      unreactAll()
    }
  }

  /** An event emitter that can be used with the `mutate` block.
   *  
   *  Calling `mutate` with this bind emitter will add a subscription
   *  to the emitter that can unsubscribe from that `mutate` statement.
   *
   *  For most purposes, clients should just use the regular `Events.Emitter`.
   *
   *  @tparam T     the type of events in the bind emitter
   */
  class BindEmitter[@spec(Int, Long, Double) T]
  extends Events[T] with Default[T] with EventSource
  with ReactMutable.Subscriptions {
    private var live = true
    def react(value: T) {
      if (live) reactAll(value)
    }
    def except(t: Throwable) {
      if (live) exceptAll(t)
    }
    def mutation() {}
    def exception(t: Throwable) = except(t)
    def unreact(): Unit = if (live) {
      live = false
      unreactAll()
    }
  }

  /** Uses the specified function `f` to produce an event when the `react` method
   *  is called.
   */
  class SideEffectEmitter[@spec(Int, Long, Double) T](f: () => T)
  extends Events.Default[T] with EventSource {
    private var live = true
    final def react() {
      if (live) {
        val event = {
          try {
            f()
          } catch {
            case t if isNonLethal(t) =>
              exceptAll(t)
              return
          }
        }
        reactAll(event)
      }
    }
    final def unreact() = if (live) {
      live = false
      unreactAll()
    }
  }

}
