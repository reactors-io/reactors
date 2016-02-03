package org.reactors






/** A basic event stream.
 *
 *  Event streams are special objects that may produce events of a certain type `T`.
 *  Clients may subscribe side-effecting functions (i.e. callbacks)
 *  to these events with `onReaction`, `onEvent`, `onMatch` and `on` --
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
 *  With the exception of `onX` family of methods, and unless otherwise specified,
 *  operators passed to these declarative combinators should be pure -- they should not
 *  have any side-effects.
 *
 *  The result of applying a declarative combinator on `Events[T]` is usually
 *  another `Events[S]`, possibly with a type parameter `S` different from `T`.
 *  Event sink combinators, such as `onEvent`, return a `Subscription` object used to
 *  `unsubscribe` from their event source.
 *
 *  Event streams are specialized for `Int`, `Long` and `Double`.
 *
 *  Every event stream is bound to a specific `Reactor`, the basic unit of concurrency.
 *  Within a reactor, at most a single event stream propagates events at a time.
 *  An event stream will only produce events during the execution of that reactor --
 *  events are never triggered on a different reactor.
 *  It is forbidden to share event streams between reactors.
 *
 *  @author        Aleksandar Prokopec
 *
 *  @tparam T      type of the events in this event stream
 */
trait Events[@spec(Int, Long, Double) T] {

  /** Registers a new `observer` to this event stream.
   *
   *  The `observer` argument may be invoked multiple times -- whenever an event is
   *  produced, or an exception occurs, and at most once when the event stream is
   *  terminated. After the event stream terminates, no events or exceptions are
   *  propagated on this event stream any more.
   *
   *  @param ovserver    the observer for `react`, `except` and `unreact` events
   *  @return            a subscription for unsubscribing from reactions
   */
  def observe(observer: Observer[T]): Subscription

}
