package io.reactors
package protocol



import scala.concurrent.duration._



/** General communication patterns.
 *
 *  Allows specifying communication patterns in a generic way.
 *  For example, retry server requests until a timeout:
 *
 *  {{{
 *  Seq(1, 2, 4).toEvents.throttle(x => x.seconds).retry(server ? "req")
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

    /** Retries the specified `request` until receiving an event or a `timeout`.
     *
     *  @param request   function that maps each event to an event stream with replies
     *  @return          an event stream with tuples of requests and replies
     */
    def retry[S](request: T => Events[S]): Events[(T, Events[S])] =
      new Patterns.Retry(events, request)
  }
}


object Patterns {
  private[protocol] class Throttle[T](val events: Events[T], val f: T => Duration)
  extends Events[T] {
    def onReaction(obs: Observer[T]) = ???
  }

  private[protocol] class Retry[T, S](val events: Events[T], val req: T => Events[S])
  extends Events[(T, Events[S])] {
    def onReaction(obs: Observer[(T, Events[S])]) = ???
  }
}
