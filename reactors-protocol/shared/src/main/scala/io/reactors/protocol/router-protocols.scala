package io.reactors
package protocol



import scala.collection._
import scala.util.Random



/** Communication patterns for routing.
 */
trait RouterProtocols {
  self: Patterns =>

  implicit class RouterChannelBuilderOps(val builder: ChannelBuilder) {
    /** Creates a new channel with the router signature.
     */
    def router[@spec(Int, Long, Double) T: Arrayable]: Connector[T] = builder.open[T]
  }

  implicit class RouterConnectorOps[@spec(Int, Long, Double) T](
    val conn: Connector[T]
  ) {
    /** Installs routing logic to the router connector.
     *
     *  The router channel routes incoming events to some channel, defined by the
     *  `selector` function.
     *
     *  @param selector  function that selects a channel for the given event
     *  @return          a connector for the router channel
     */
    def route(selector: T => Channel[T]): Connector[T] = {
      conn.events.onEvent { x =>
        selector(x) ! x
      }
      conn
    }
  }
}


/** Contains types and factory functions for router protocols.
 */
object Router {
  /** Type of a function that selects a channel given an event.
   */
  type Selector[T] = T => Channel[T]

  /** Always returns a zero channel, which loses all the events sent to it.
   *
   *  @tparam T       type of the events to route
   *  @return         a selector function that drops events
   */
  def zeroSelector[T]: Selector[T] = (x: T) => new Channel.Zero[T]

  /** Picks channels in a Round Robin manner.
   *
   *  @tparam T       type of the events to route
   *  @param targets  the channels to route the events to
   *  @return         a selector function that chooses a channel
   */
  def roundRobin[T](targets: Seq[Channel[T]]): Selector[T] = {
    var i = -1
    (x: T) => {
      if (targets.nonEmpty) {
        i = (i + 1) % targets.length
        val ch = targets(i)
        ch
      } else new Channel.Zero[T]
    }
  }

  /** Picks a channel from a random distribution.
   *
   *  @tparam T        type of the events routed
   *  @param targets   target channels to which to route the events
   *  @param randfun   randomization function, total number of channels to an index
   *  @return          the selector function
   */
  def random[T](
    targets: Seq[Channel[T]],
    randfun: Int => Int = {
      val r = new Random
      (n: Int) => r.nextInt(n)
    }
  ): Selector[T] = {
    (x: T) => {
      if (targets.nonEmpty) targets(randfun(targets.length))
      else new Channel.Zero[T]
    }
  }

  /** Consistently picks a channel using a hashing function on the event.
   *
   *  The hashing function is applied to the event, and the hash code is used to
   *  select a channel. Given that the same hash code is always returned for the same
   *  event, the same channel is always picked. This is useful when implementing, e.g.
   *  distributed hash tables.
   *
   *  @tparam T        type of the events routed
   *  @param targets   target channels to which events are routed
   *  @param hashing   hashing function from an event to some hash value
   *  @return          the selector function
   */
  def hash[T](
    targets: Seq[Channel[T]],
    hashing: T => Int = (x: T) => x.##
  ): Selector[T] = {
    (x: T) => {
      if (targets.nonEmpty) targets(hashing(x) % targets.length)
      else new Channel.Zero[T]
    }
  }

  /** Picks the next channel according to the Deficit Round Robin routing algorithm.
   *
   *  This routing policy attempts to send the message to the channel that has so far
   *  received the least total cost, according to some cost function `cost`.
   *  The cost of an event could be its size (if the network transmission is the main
   *  concern), or the estimate on the processing time of that event (if computing
   *  bandwidth is the main concern).
   *
   *  Each target channel has an associated deficit counter, which is increased by
   *  an amount called a `quantum` each time a channel gets selected, and decreased
   *  every time that an event is sent to it. When an event with a cost higher than
   *  the deficit counter appears, the next channel is selected.
   *
   *  '''Note:''' quantum and cost should be relatively close in magnitude.
   *
   *  @tparam T       type of routed events
   *  @param targets  sequence of target channels
   *  @param quantum  the base cost quantum used to increase 
   *  @param cost     function from an event to its cost
   *  @return         a selector
   */
  def deficitRoundRobin[T](
    targets: immutable.Seq[Channel[T]],
    quantum: Int,
    cost: T => Int
  ): Selector[T] = {
    if (targets.isEmpty) zeroSelector
    else {
      val deficits = new Array[Int](targets.length)
      var i = targets.length - 1
      (x: T) => {
        val c = cost(x)
        var found = false
        while (!found) {
          if (deficits(i) > c) found = true
          else {
            i = (i + 1) % targets.length
            deficits(i) += quantum
          }
        }
        deficits(i) -= c
        targets(i)
      }
    }
  }
}
