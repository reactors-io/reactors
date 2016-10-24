package io.reactors
package protocol






trait BackpressureProtocols {
  self: LinkProtocols =>

  object Backpressure {
    type Req[T] = Server.Req[TwoWay[Int, T], Unit]

    case class Connection[T](events: Events[T], subscription: Subscription)

    case class Policy[T](
      server: TwoWay[Int, T] => Backpressure.Connection[T],
      client: TwoWay[T, Int] => Link[T]
    )

    object Policy {
      def sliding[T: Arrayable](size: Int) = Backpressure.Policy[T](
        server = twoWay => {
          twoWay.input ! size
          val tokenSubscription = twoWay.output on {
            twoWay.input ! 1
          }
          Backpressure.Connection(
            twoWay.output,
            tokenSubscription.chain(twoWay.subscription)
          )
        },
        client = twoWay => {
          val system = Reactor.self.system
          val frontend = system.channels.daemon.shortcut.open[T]
          val increments = twoWay.output
          val decrements = frontend.events.map(x => -1)
          val available = (increments union decrements).scanPast(0) {
            (acc, v) => acc + v
          }.map(_ > 0).toSignal(false)
          val forwarding = frontend.events.onEvent(x => twoWay.input ! x)
          Link(
            frontend.channel,
            available,
            forwarding.chain(available).chain(twoWay.subscription)
          )
        }
      )
      def batch[T: Arrayable](size: Int): Backpressure.Policy[T] = {
        val slidingPolicy = sliding[T](size)
        Backpressure.Policy[T](
          server = twoWay => {
            twoWay.input ! size
            val tokens = RCell(0)
            val tokenSubscription = twoWay.output on {
              tokens := tokens() + 1
            }
            val flushSubscription = Reactor.self.sysEvents onMatch {
              case ReactorPreempted =>
                twoWay.input ! tokens()
                tokens := 0
            }
            Backpressure.Connection(
              twoWay.output,
              tokenSubscription.chain(flushSubscription).chain(twoWay.subscription)
            )
          },
          client = slidingPolicy.client
        )
      }
    }
  }

  implicit class BackpressureTwoWayOps[T: Arrayable](val twoWay: TwoWay[Int, T]) {
    def backpressure(
      policy: Backpressure.Policy[T] = Backpressure.Policy.sliding[T](128)
    ): Backpressure.Connection[T] = {
      policy.server(twoWay)
    }
  }
}
