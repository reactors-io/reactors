package org.reactors



import org.reactors.common._



/** A special type of an event stream that caches the last emitted event.
 *
 *  This last event is called the signal's ''value''.
 *  It can be read using the `Signal`'s `apply` method.
 *  
 *  @tparam T        the type of the events in this signal
 */
trait Signal[@spec(Int, Long, Double) T] extends Events[T] {

  /** Returns the last event produced by `this` signal.
   *
   *  
   *
   *  @return         the signal's value
   *  @throws         `NoSuchElementException` if the signal does not contain an event
   */
  def apply(): T

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
   *  Whenever either of the two signals change the resulting signal also
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

}


object Signal {

  class Changes[@spec(Int, Long, Double) T](val self: Signal[T]) extends Events[T] {
    def onReaction(obs: Observer[T]) =
      self.onReaction(new Signal.ChangesObserver[T](obs, self()))
  }

  class ChangesObserver[@spec(Int, Long, Double) T](
    val target: Observer[T],
    var cached: T
  ) extends Observer[T] {
    def react(x: T) = if (cached != x) {
      cached = x
      target.react(x)
    }
    def except(t: Throwable) = target.except(t)
    def unreact() = target.unreact()
  }

  class DiffPast[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](
    val self: Signal[T],
    val op: (T, T) => S
  ) extends Events[S] {
    def onReaction(obs: Observer[S]): Subscription =
      self.onReaction(new DiffPastObserver(obs, op, self()))
  }

  class DiffPastObserver[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](
    val target: Observer[S],
    val op: (T, T) => S,
    var last: T
  ) extends Observer[T] {
    def react(x: T) {
      val d = try {
        op(x, last)
      } catch {
        case NonLethal(t) =>
          target.except(t)
          return
      }
      last = x
      target.react(d)
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
    def react(x: T): Unit = if (!done) {
      val event = try {
        f(x, that())
      } catch {
        case NonLethal(t) =>
          target.except(t)
          return
      }
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
    def react(x: S): Unit = if (!thisObserver.done) {
      val event = try {
        f(thisObserver.self(), x)
      } catch {
        case NonLethal(t) =>
          target.except(t)
          return
      }
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

}
