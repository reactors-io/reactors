package io.reactors
package protocol






trait BackpressureProtocols {
  self: CommunicationAbstractions =>

  case class Backpressure[T]()

  object Backpressure {
    case class Connection[T](events: Events[T], subscription: Subscription)
    extends io.reactors.protocol.Connection[T]

    case class Server[T](
      connections: Events[Connection[T]],
      subscription: Subscription
    ) extends ServerSide[Connection[T]]

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
          val forwarding = frontend.events.onEvent { x =>
            if (available()) twoWay.input ! x
          }
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

  implicit class BackpressureTwoWayServerOps[T: Arrayable](val twoWay: TwoWay[Int, T]) {
    def toBackpressureConnection(
      policy: Backpressure.Policy[T] = Backpressure.Policy.sliding[T](128)
    ): Backpressure.Connection[T] = {
      policy.server(twoWay)
    }
  }

  implicit class BackpressureTwoWayClientOps[T: Arrayable](val twoWay: TwoWay[T, Int]) {
    def toBackpressureLink(
      policy: Backpressure.Policy[T] = Backpressure.Policy.sliding[T](128)
    ): Link[T] = {
      policy.client(twoWay)
    }
  }

  implicit class BackpressureConnectionOps[T: Arrayable](
    val serverSide: ServerSide[TwoWay[Int, T]]
  ) {
    def toBackpressure(
      policy: Backpressure.Policy[T] = Backpressure.Policy.sliding[T](128)
    ): Backpressure.Server[T] = {
      val backpressureConnections =
        serverSide.connections.map(_.toBackpressureConnection(policy))
      Backpressure.Server(backpressureConnections, serverSide.subscription)
    }
  }

  implicit class BackpressureTwoWayIVarOps[T: Arrayable](
    val connections: IVar[TwoWay[T, Int]]
  ) {
    def toBackpressure(
      policy: Backpressure.Policy[T] = Backpressure.Policy.sliding[T](128)
    ): IVar[Link[T]] = {
      connections.map(_.toBackpressureLink(policy)).toIVar
    }
  }
}
