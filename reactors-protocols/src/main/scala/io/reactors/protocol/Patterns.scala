package io.reactors
package protocol



import io.reactors.common.UnrolledRing
import scala.concurrent.duration._
import scala.util._



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
 *    .first.until(timeout(3.seconds))
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
     *  Note that exceptions on the current stream `events` result in unreacting the
     *  resulting event stream.
     *
     *  @param f         function that converts each event into a delay
     *  @return          an event stream with the throttled events
     */
    def throttle(f: T => Duration)(implicit a: Arrayable[T]): Events[T] =
      new Patterns.Throttle(events, f)
  }
}


object Patterns {
  private[protocol] class Throttle[T](
    val events: Events[T], val f: T => Duration
  )(
    implicit val a: Arrayable[T]
  ) extends Events[T] {
    def onReaction(obs: Observer[T]) =
      events.onReaction(new ThrottleObserver(obs, f))
  }

  private[protocol] class ThrottleObserver[T](
    val target: Observer[T], val f: T => Duration
  )(
    implicit val a: Arrayable[T]
  ) extends Observer[T] {
    var done = false
    var queue = new UnrolledRing[T]
    def react(x: T, hint: Any) {
      def emitNext() {
        val x = queue.head
        val duration = try {
          f(x)
        } catch {
          case NonLethal(t) =>
            except(t)
            return
        }
        target.react(x, null)
        Reactor.self.system.clock.timeout(duration) on {
          queue.dequeue()
          if (queue.nonEmpty) emitNext()
        }
      }

      if (queue.isEmpty) {
        queue.enqueue(x)
        emitNext()
      } else {
        queue.enqueue(x)
      }
    }
    def except(t: Throwable) = {
      target.except(t)
      unreact()
    }
    def unreact() {
      done = true
      target.unreact()
    }
  }
}
