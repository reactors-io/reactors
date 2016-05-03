package io.reactors
package protocol






/** Communication patterns for load balancing.
 */
trait BalancerProtocols {
  self: Patterns =>

  implicit class BalancerChannelBuilderOps(val builder: ReactorSystem.ChannelBuilder) {
    /** Create a new balancer channel.
     */
    def balancer[@spec(Int, Long, Double) T: Arrayable](
      targets: Seq[Channel[T]]
    ): Connector[T] = {
      val conn = builder.open[T]
      var i = 0
      conn.events.onEvent { x =>
        if (targets.length > 0) {
          targets(i) ! x
          i = (i + 1) % targets.length
        }
      }
      conn
    }
  }
}
