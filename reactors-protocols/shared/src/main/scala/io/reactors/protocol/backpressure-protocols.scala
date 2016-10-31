package io.reactors
package protocol






trait BackpressureProtocols {
  case class Backpressure[T]()

  object Backpressure {
    case class Connection[T](events: Events[T], subscription: Subscription)
    extends io.reactors.protocol.Connection[T]

    case class Server[T, R](
      channel: Channel[R],
      connections: Events[Connection[T]],
      subscription: Subscription
    ) extends ServerSide[R, Connection[T]]

    case class Medium[R, T](
      openServer: ChannelBuilder => ServerSide[R, TwoWay[Int, T]],
      openClient: Channel[R] => IVar[TwoWay[T, Int]]
    )

    object Medium {
      def default[T: Arrayable] = Backpressure.Medium[TwoWay.Req[T, Int], T](
        builder => builder.twoWayServer[T, Int].serveTwoWay(),
        channel => channel.connect()
      )

      def reliable[T: Arrayable] = Backpressure.Medium[Reliable.TwoWay.Req[T, Int], T](
        builder => builder.reliableTwoWayServer[T, Int].serveTwoWayReliable(),
        channel => channel.connectReliable()
      )
    }

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

  implicit class BackpressureChannelBuilderOps[T: Arrayable](
    val builder: ChannelBuilder
  ) {
    def serveBackpressure[R](
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T] = Backpressure.Policy.sliding[T](128)
    ): Backpressure.Server[T, R] = {
      val twoWayServer = medium.openServer(builder)
      Backpressure.Server(
        twoWayServer.channel,
        twoWayServer.connections.map(_.toBackpressureConnection(policy)),
        twoWayServer.subscription
      )
    }
  }

  implicit class BackpressureServerOps[R, T: Arrayable](
    val server: Channel[R]
  ) {
    def connectBackpressure(
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T] = Backpressure.Policy.sliding[T](128)
    ): Unit = {
      medium.openClient(server).map(_.toBackpressureLink(policy))
    }
  }
}
