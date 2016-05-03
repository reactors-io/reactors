package io.reactors
package protocol



import scala.util.Random



/** Communication patterns for load balancing.
 */
trait BalancerProtocols {
  self: Patterns =>

  object Balancer {
    /** Describes how to pick the next channel.
     */
    abstract class Policy {
      /** For a sequence of channels, returns a function that picks the next channel.
       *
       *  The returned function implements a specific policy for picking channels.
       */
      def apply[T](targets: Seq[Channel[T]]): () => Channel[T] = {
        if (targets.length == 0) {
          val ch = new Channel.Zero[T]
          () => ch
        } else {
          forNonEmpty(targets)
        }
      }
      /** Same as `apply`, but assumes non-empty `targets`.
       */
      protected def forNonEmpty[T](targets: Seq[Channel[T]]): () => Channel[T]
    }

    object Policy {
      /** Picks channels in a round-robin manner.
       */
      object RoundRobin extends Policy {
        protected def forNonEmpty[T](targets: Seq[Channel[T]]) = {
          var i = 0
          () => {
            val ch = targets(i)
            i = (i + 1) % targets.length
            ch
          }
        }
      }

      /** Picks channels from a random uniform distribution.
       */
      object Uniform extends Policy {
        protected def forNonEmpty[T](targets: Seq[Channel[T]]) = {
          val rand = new Random
          () => targets(rand.nextInt(targets.length))
        }
      }
    }
  }

  implicit class BalancerChannelBuilderOps(val builder: ReactorSystem.ChannelBuilder) {
    /** Create a new balancer channel.
     *
     *  The balancer channel balances incoming events across a sequence of channels.
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
