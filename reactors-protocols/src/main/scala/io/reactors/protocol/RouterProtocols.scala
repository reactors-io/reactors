package io.reactors
package protocol



import scala.util.Random



/** Communication patterns for routing.
 */
trait RouterProtocols {
  self: Patterns =>

  object Router {
    /** Type of a function that selects a channel given an event.
     */
    type Selector[T] = T => Channel[T]

    /** Always returns a zero channel, which loses all the events sent to it.
     */
    def zeroSelector[T]: Selector[T] = (x: T) => new Channel.Zero[T]

    /** Picks channels in a round-robin manner.
     */
    def roundRobin[T](targets: Seq[Channel[T]]): Selector[T] = {
      if (targets.isEmpty) zeroSelector
      else {
        var i = 0
        (x: T) => {
          val ch = targets(i)
          i = (i + 1) % targets.length
          ch
        }
      }
    }

    /** Picks a channel from a random uniform distribution.
     */
    def uniform[T](
      targets: Seq[Channel[T]],
      random: Int => Int = {
        val r = new Random
        (n: Int) => r.nextInt(n)
      }
    ): Selector[T] = {
      if (targets.isEmpty) zeroSelector
      else (x: T) => targets(random(targets.length))
    }

    /** Consistently picks a channel using a hashing function on the event.
     */
    def hash[T](
      targets: Seq[Channel[T]],
      hashing: T => Int = (x: T) => x.##
    ): Selector[T] = {
      if (targets.isEmpty) zeroSelector
      else (x: T) => targets(hashing(x) % targets.length)
    }
  }

  implicit class RouterChannelBuilderOps(val builder: ChannelBuilder) {
    /** Create a new router channel.
     *
     *  The router channel routes incoming events to some channel, defined by the
     *  `selector` function.
     *
     *  @tparam T        the type of the routed events
     *  @param selector  function that selects a channel for the given event
     */
    def router[@spec(Int, Long, Double) T: Arrayable](
      selector: T => Channel[T]
    ): Connector[T] = {
      val conn = builder.open[T]
      conn.events.onEvent { x =>
        selector(x) ! x
      }
      conn
    }
  }
}
