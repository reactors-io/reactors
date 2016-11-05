package io.reactors
package protocol






trait BackpressureProtocols {
  case class Backpressure[T]()

  object Backpressure {
    case class Server[R, T](
      channel: Channel[R],
      connections: Events[Pump[T]],
      subscription: Subscription
    ) extends ServerSide[R, Pump[T]]

    case class Medium[R, T](
      openServer: ChannelBuilder => Connector[R],
      serve: Connector[R] => ServerSide[R, TwoWay[Int, T]],
      connect: Channel[R] => IVar[TwoWay[T, Int]]
    )

    object Medium {
      def default[T: Arrayable] = Backpressure.Medium[TwoWay.Req[T, Int], T](
        builder => builder.twoWayServer[T, Int],
        connector => connector.serveTwoWay(),
        channel => channel.connect()
      )

      def reliable[T: Arrayable] = Backpressure.Medium[Reliable.TwoWay.Req[T, Int], T](
        builder => builder.reliableTwoWayServer[T, Int],
        connector => connector.serveTwoWayReliable(),
        channel => channel.connectReliable()
      )
    }

    case class Policy[T](
      server: TwoWay[Int, T] => Pump[T],
      client: TwoWay[T, Int] => Valve[T]
    )

    object Policy {
      def sliding[T: Arrayable](size: Int) = Backpressure.Policy[T](
        server = twoWay => {
          twoWay.input ! size
          val buffer = twoWay.output.toEventBuffer
          val tokenSubscription = buffer on {
            twoWay.input ! 1
          }
          Pump(
            buffer,
            tokenSubscription.chain(twoWay.subscription).chain(buffer)
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
          Valve(
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
            val buffer = twoWay.output.toEventBuffer
            val tokens = RCell(0)
            val tokenSubscription = buffer on {
              tokens := tokens() + 1
            }
            val flushSubscription = Reactor.self.sysEvents onMatch {
              case ReactorPreempted =>
                twoWay.input ! tokens()
                tokens := 0
            }
            Pump(
              buffer,
              tokenSubscription
                .chain(flushSubscription)
                .chain(twoWay.subscription)
                .chain(buffer)
            )
          },
          client = slidingPolicy.client
        )
      }
    }
  }

  implicit class BackpressureChannelBuilderOps[R, T](val builder: ChannelBuilder) {
    def backpressureServer(medium: Backpressure.Medium[R, T]): Connector[R] = {
      medium.openServer(builder)
    }
  }

  implicit class BackpressureConnectorOps[R, T](
    val connector: Connector[R]
  ) {
    def serveBackpressure(
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T]
    )(implicit a: Arrayable[T]): Backpressure.Server[R, T] = {
      val twoWayServer = medium.serve(connector)
      Backpressure.Server(
        twoWayServer.channel,
        twoWayServer.connections.map(policy.server),
        twoWayServer.subscription
      )
    }
  }

  implicit class BackpressureServerOps[R, T](
    val server: Channel[R]
  ) {
    def connectBackpressure(
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T]
    ): IVar[Valve[T]] = {
      medium.connect(server).map(policy.client).toIVar
    }
  }

  implicit class BackpressureSystemOps(
    val system: ReactorSystem
  ) {
    def backpressureServer[R: Arrayable, T: Arrayable](
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T]
    )(f: Backpressure.Server[R, T] => Unit): Channel[R] = {
      val proto = Reactor[R] { self =>
        f(self.main.serveBackpressure(medium, policy))
      }
      system.spawn(proto)
    }
  }
}
