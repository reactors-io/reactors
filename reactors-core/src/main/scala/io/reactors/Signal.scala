package io.reactors



import io.reactors.common._



/** A special type of an event stream that caches the last emitted event.
 *
 *  This last event is called the signal's ''value''.
 *  It can be read using the `Signal`'s `apply` method.
 *  
 *  @tparam T        the type of the events in this signal
 */
trait Signal[@spec(Int, Long, Double) T] extends Events[T] with Subscription {

  /** Returns the last event produced by `this` signal.
   *
   *  @return         the signal's value
   *  @throws         `NoSuchElementException` if the signal does not contain an event
   */
  def apply(): T

  /** Returns `true` iff this signal does not have any value yet.
   */
  def isEmpty: Boolean

  /** Returns `true` iff this signal has a value.
   */
  final def nonEmpty: Boolean = !isEmpty

  /** An event stream that only emits events when the value of `this` signal changes.
   *
   *  {{{
   *  time    --------------->
   *  this    --1---2--2--3-->
   *  changes --1---2-----3-->
   *  }}}
   *
   *  @return         a subscription and the signal with changes of `this`
   */
  def changes: Events[T] = new Signal.Changes(this)

  /** A signal that produces difference events between the current and previous
   *  value of `this` signal.
   *
   *  {{{
   *  time ---------------->
   *  this --1--3---6---7-->
   *  diff --z--2---3---1-->
   *  }}}
   *  
   *  @tparam S       the type of the difference event
   *  @param z        the initial value for the difference
   *  @param op       the operator that computes the difference between
   *                  consecutive events
   *  @return         a subscription and a signal with the difference value
   */
  def diffPast[@spec(Int, Long, Double) S](op: (T, T) => S): Events[S] =
    new Signal.DiffPast(this, op)

  /** Zips values of `this` and `that` signal using the specified function `f`.
   *
   *  Whenever either of the two signals changes, the resulting signal also
   *  changes.
   *  When `this` emits an event, the current value of `that` is used to produce
   *  a signal on `that`, and vice versa.
   *
   *  {{{
   *  time --------------------------------->
   *  this --1----2-----4----------8-------->
   *  that --a----------------b---------c--->
   *  zip  --1,a--2,a---4,a---4,b--8,b--8,c->
   *  }}}
   *
   *  When either of the input signals unreacts, the resulting signal also
   *  unreacts.
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
   *  you should use the `sync` method inherited from `Events`.
   *
   *  @tparam S        the type of `that` signal
   *  @tparam R        the type of the resulting signal
   *  @param that      the signal to zip `this` with
   *  @param f         the function that maps a tuple of values into an outgoing
   *                   event
   *  @return          a subscription and the event stream that emits zipped events
   */
  def zip[@spec(Int, Long, Double) S, @spec(Int, Long, Double) R](
    that: Signal[S]
  )(f: (T, S) => R): Events[R] =
    new Signal.Zip[T, S, R](this, that, f)

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
   *  @return         a subscription and a signal of tuples of the current and
   *                  last event
   */
  def past2(init: T): Events[(T, T)] = scanPast((init, this())) { (t, x) => (t._2, x) }

  /** Returns another signal with the same value, and an additional subscription.
   *
   *  @param s        the additional subscription to unsubscribe to
   *  @return         a signal with the same events and value, and an additional
   *                  subscription
   */
  def withSubscription(s: Subscription): Signal[T] =
    new Signal.WithSubscription(this, s)

}


object Signal {

  /** Signal with a constant value.
   */
  class Const[@spec(Int, Long, Double) T](private[reactors] val value: T)
  extends Signal[T] {
    def apply() = value
    def isEmpty = false
    def unsubscribe() = {}
    def onReaction(obs: Observer[T]) = {
      obs.react(value, null)
      obs.unreact()
      Subscription.empty
    }
  }

  /** Signal containing a mutable value.
   *
   *  Value can be accessed with the `apply` method.
   *  To modify the content, clients must use the `mutate` method on event streams.
   */
  class Mutable[M >: Null <: AnyRef](c: M) extends Events.Mutable[M](c) {
    def apply(): M = content
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
   *
   *  {{{
   *  val emitters = for (0 until 10) yield new Events.Emitter[Int]
   *  val ag = Signal.aggregate(emitters)(_ + _)
   *  }}}
   *
   *  The aggregation operator needs to be associative, but does not need to be
   *  commutative.
   *  For example, string concatenation for signals of strings or addition for integer
   *  signals are valid operators. Subtraction for integer signals, for example, is not
   *  associative and not allowed.
   *
   *  The value `z` for the aggregation does not need to be a neutral element with
   *  respect to the aggregation operation.
   *
   *  The resulting signal is hot, i.e. its value is updated even if there are no
   *  subscribers.
   *
   *  @tparam T       type of the aggregate signal
   *  @param ss       signals for the aggregation
   *  @param z        the zero value of the aggregation, used if the list is empty
   *  @param op       the aggregation operator, must be associative
   */
  def aggregate[@spec(Int, Long, Double) T](ss: Signal[T]*)(z: T)(op: (T, T) => T):
    Signal[T] = {
    if (ss.length == 0) new Signal.Const(z)
    else {
      var levelsigs: Seq[Signal[T]] = ss
      while (levelsigs.length != 1) {
        val nextLevel = for (pair <- levelsigs.grouped(2)) yield pair match {
          case Seq(s1, s2) =>
            val zipped = (s1 zip s2)((x, y) => op(x, y))
            zipped.toCold(op(s1(), s2()))
          case Seq(s) => s
        }
        levelsigs = nextLevel.toBuffer
      }
      levelsigs(0).toSignal(levelsigs(0)())
    }
  }

  private[reactors] class Changes[@spec(Int, Long, Double) T](val self: Signal[T])
  extends Events[T] {
    def onReaction(obs: Observer[T]) =
      self.onReaction(new Signal.ChangesObserver[T](obs, self()))
  }

  private[reactors] class ChangesObserver[@spec(Int, Long, Double) T](
    val target: Observer[T],
    var cached: T
  ) extends Observer[T] {
    def react(x: T, hint: Any) = if (cached != x) {
      cached = x
      target.react(x, hint)
    }
    def except(t: Throwable) = target.except(t)
    def unreact() = target.unreact()
  }

  private[reactors] class DiffPast[
    @spec(Int, Long, Double) T,
    @spec(Int, Long, Double) S
  ](
    val self: Signal[T],
    val op: (T, T) => S
  ) extends Events[S] {
    def onReaction(obs: Observer[S]): Subscription =
      self.onReaction(new DiffPastObserver(obs, op, self()))
  }

  private[reactors] class DiffPastObserver[
    @spec(Int, Long, Double) T,
    @spec(Int, Long, Double) S
  ](
    val target: Observer[S],
    val op: (T, T) => S,
    var last: T
  ) extends Observer[T] {
    def react(x: T, hint: Any) {
      val d = try {
        op(x, last)
      } catch {
        case NonLethal(t) =>
          target.except(t)
          return
      }
      last = x
      target.react(d, hint)
    }
    def except(t: Throwable) = target.except(t)
    def unreact() = target.unreact()
  }

  private[reactors] class Zip[
    @spec(Int, Long, Double) T,
    @spec(Int, Long, Double) S,
    @spec(Int, Long, Double) R
  ](
    val self: Signal[T],
    val that: Signal[S],
    val f: (T, S) => R
  ) extends Events[R] {
    def newZipThisObserver(obs: Observer[R]) =
      new ZipThisObserver(obs, f, self, that)
    def newZipThatObserver(obs: Observer[R], thisObs: ZipThisObserver[T, S, R]) =
      new ZipThatObserver(obs, f, thisObs)
    def onReaction(obs: Observer[R]) = {
      val thisObs = newZipThisObserver(obs)
      val thatObs = newZipThatObserver(obs, thisObs)
      val sub = new Subscription.Composite(
        self.onReaction(thisObs),
        that.onReaction(thatObs)
      )
      thisObs.subscription = sub
      sub
    }
  }

  private[reactors] class ZipThisObserver[
    @spec(Int, Long, Double) T,
    @spec(Int, Long, Double) S,
    @spec(Int, Long, Double) R
  ](
    val target: Observer[R],
    val f: (T, S) => R,
    val self: Signal[T],
    val that: Signal[S]
  ) extends Observer[T] {
    var subscription: Subscription = _
    var done = false
    def react(x: T, hint: Any): Unit = if (!done) {
      val event = try {
        f(x, that())
      } catch {
        case NonLethal(t) =>
          target.except(t)
          return
      }
      target.react(event, null)
    }
    def except(t: Throwable) = if (!done) {
      target.except(t)
    }
    def unreact() = if (!done) {
      done = true
      subscription.unsubscribe()
      target.unreact()
    }
  }

  private[reactors] class ZipThatObserver[
    @spec(Int, Long, Double) T,
    @spec(Int, Long, Double) S,
    @spec(Int, Long, Double) R
  ](
    val target: Observer[R],
    val f: (T, S) => R,
    val thisObserver: ZipThisObserver[T, S, R]
  ) extends Observer[S] {
    def react(x: S, hint: Any): Unit = if (!thisObserver.done) {
      val event = try {
        f(thisObserver.self(), x)
      } catch {
        case NonLethal(t) =>
          target.except(t)
          return
      }
      target.react(event, null)
    }
    def except(t: Throwable) = if (!thisObserver.done) {
      target.except(t)
    }
    def unreact() = if (!thisObserver.done) {
      thisObserver.done = true
      thisObserver.subscription.unsubscribe()
      target.unreact()
    }
  }

  private[reactors] class WithSubscription[@spec(Int, Long, Double) T](
    val self: Signal[T],
    val subscription: Subscription
  ) extends Signal[T] {
    def onReaction(obs: Observer[T]) = self.onReaction(obs)
    def apply() = self.apply()
    def isEmpty = self.isEmpty
    def unsubscribe() {
      subscription.unsubscribe()
      self.unsubscribe()
    }
  }

}
