package io.reactors
package protocol



import scala.util.Random



/** Communication patterns for load balancing.
 */
trait BalancerProtocols {
  self: Patterns =>

  object Balancer {
    abstract class Policy {
      def apply[T](targets: Seq[Channel[T]]): () => Channel[T] = {
        if (targets.length == 0) {
          val ch = new Channel.Zero[T]
          () => ch
        } else {
          forNonEmpty(targets)
        }
      }
      def forNonEmpty[T](targets: Seq[Channel[T]]): () => Channel[T]
    }

    object Policy {
      object RoundRobin extends Policy {
        def forNonEmpty[T](targets: Seq[Channel[T]]) = {
          var i = 0
          () => {
            val ch = targets(i)
            i = (i + 1) % targets.length
            ch
          }
        }
      }

      object Rand extends Policy {
        def forNonEmpty[T](targets: Seq[Channel[T]]) = {
          val rand = new Random
          () => targets(rand.nextInt(targets.length))
        }
      }
    }
  }

  implicit class BalancerChannelBuilderOps(val builder: ReactorSystem.ChannelBuilder) {
    /** Create a new balancer channel.
     */
    def balancer[@spec(Int, Long, Double) T: Arrayable](
      targets: Seq[Channel[T]],
      strategy: Balancer.Policy = Balancer.Policy.RoundRobin
    ): Connector[T] = {
      val conn = builder.open[T]
      val select = strategy(targets)
      conn.events.onEvent { x =>
        select() ! x
      }
      conn
    }
  }
}
