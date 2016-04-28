package io.reactors
package protocol



import scala.concurrent.duration._



/** General communication patterns.
 *
 *  Allows specifying communication patterns in a generic way.
 *
 *  As an example, one can declaratively retry server requests until a timeout,
 *  by sending a throttled sequence of requests until a timeout, and taking the first
 *  reply that comes:
 *
 *  {{{
 *  Seq(1, 2, 4).toEvents.throttle(x => x.seconds).map(server ? "req")
 *    .until(timeout(3.seconds)).first
 *  }}}
 */
trait Patterns {
  implicit class DurationEventOps[T](val events: Events[T]) {
    /** Delays each event by `Duration` returned by `f`.
     *
     *  Instead of being emitted immediately, events are asynchronously delayed and
     *  emitted one after the other, separated by at least time intervals specified by
     *  the function `f`.
     *
     *  Assuming that `f == (x: Int) => x.seconds`:
     *
     *  {{{
     *  this         ---1---3-----------------2------------->
     *  throttle(f)  ----------1----------3------------2---->
     *  time             <-1s-> <---3s--->     <--2s-->
     *  }}}
     *
     *  @param f         function that converts each event into a delay
     *  @return          an event stream with the throttled events
     */
    def throttle(f: T => Duration): Events[T] = new Patterns.Throttle(events, f)
  }
}


object Patterns {
  private[protocol] class Throttle[T](val events: Events[T], val f: T => Duration)
  extends Events[T] {
    def onReaction(obs: Observer[T]) = ???
  }
}
