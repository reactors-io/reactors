package io.reactors
package protocol






trait BackpressureProtocols {
  case class Backpressure[T]()

  object Backpressure {
    case class Connection[T](
      channel: Channel[Int],
      buffer: EventBuffer[T],
      subscription: Subscription
    ) {
      def toPump: Pump[T] = {
        val pressureSubscription = buffer.on(channel ! 1)
        Pump(
          buffer,
          pressureSubscription.chain(subscription)
        )
      }
    }

    case class Server[R, T](
      channel: Channel[R],
      connections: Events[Connection[T]],
      subscription: Subscription
    ) extends ServerSide[R, Connection[T]] {
      def toPumpServer: PumpServer[R, T] = {
        Backpressure.PumpServer(
          channel,
          connections.map(_.toPump),
          subscription
        )
      }
    }

    case class PumpServer[R, T](
      channel: Channel[R],
      connections: Events[Pump[T]],
      subscription: Subscription
    ) extends ServerSide[R, Pump[T]]

    case class Medium[R, T](
      openServer: ChannelBuilder => Connector[R],
      serve: Connector[R] => ServerSide[R, TwoWay[T, Int]],
      connect: Channel[R] => IVar[TwoWay[Int, T]]
    )

    object Medium {
      def default[T: Arrayable] = Backpressure.Medium[TwoWay.Req[Int, T], T](
        builder => builder.twoWayServer[Int, T],
        connector => connector.serveTwoWay(),
        channel => channel.connect()
      )

      def reliable[T: Arrayable](policy: Reliable.TwoWay.Policy[Int, T]) =
        Backpressure.Medium[Reliable.TwoWay.Req[Int, T], T](
          builder => builder.reliableTwoWayServer[Int, T],
          connector => connector.serveTwoWayReliable(policy),
          channel => channel.connectReliable(policy)
        )
    }

    case class Policy[T](
      server: (Events[Int], Channel[Int]) => Subscription,
      client: TwoWay[Int, T] => Valve[T]
    )

    object Policy {
      private def defaultClient[T: Arrayable](size: Int): TwoWay[Int, T] => Valve[T] = {
        twoWay => {
          val system = Reactor.self.system
          val frontend = system.channels.daemon.shortcut.open[T]
          val budget = RCell(0)
          val available = budget.map(_ > 0).toSignal(false)
          val increments = twoWay.input.onEvent(x => budget := budget() + x)
          val forwarding = frontend.events.onEvent { x =>
            if (available()) twoWay.output ! x
            else throw new IllegalStateException("Backpressure channel not available.")
            budget := budget() - 1
          }
          Valve(
            frontend.channel,
            available,
            forwarding.chain(increments).chain(twoWay.subscription)
          )
        }
      }

      def sliding[T: Arrayable](size: Int) = Backpressure.Policy[T](
        server = (inputPressure, outputPressure) => {
          outputPressure ! size
          inputPressure onEvent { n =>
            outputPressure ! n
          }
        },
        client = defaultClient[T](size)
      )

      def batching[T: Arrayable](size: Int): Backpressure.Policy[T] = {
        Backpressure.Policy[T](
          server = (inputPressure, outputPressure) => {
            outputPressure ! size
            val tokens = RCell(0)
            val tokenSubscription = inputPressure onEvent { n =>
              tokens := tokens() + n
            }
            val flushSubscription = Reactor.self.sysEvents onMatch {
              case ReactorPreempted =>
                outputPressure ! tokens()
                tokens := 0
            }
            tokenSubscription.chain(flushSubscription)
          },
          client = defaultClient[T](size)
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
    def serveGenericBackpressure(
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T]
    )(implicit a: Arrayable[T]): Backpressure.Server[R, T] = {
      val twoWayServer = medium.serve(connector)
      Backpressure.Server(
        twoWayServer.channel,
        twoWayServer.connections.map {
          case TwoWay(channel, events, twoWaySub) =>
            val system = Reactor.self.system
            val pressure = system.channels.daemon.shortcut.open[Int]
            val sub = policy.server(pressure.events, channel).chain(twoWaySub)
            Backpressure.Connection(pressure.channel, events.toEventBuffer, sub)
        },
        twoWayServer.subscription
      )
    }

    def serveBackpressure(
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T]
    )(implicit a: Arrayable[T]): Backpressure.PumpServer[R, T] = {
      serveGenericBackpressure(medium, policy).toPumpServer
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

  implicit class BackpressureReactorCompanionOps(
    val reactor: Reactor.type
  ) {
    def genericBackpressureServer[R: Arrayable, T: Arrayable](
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T]
    )(f: Backpressure.Server[R, T] => Unit): Proto[Reactor[R]] = {
      Reactor[R] { self =>
        f(self.main.serveGenericBackpressure(medium, policy))
      }
    }

    def backpressureServer[R: Arrayable, T: Arrayable](
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T]
    )(f: Backpressure.PumpServer[R, T] => Unit): Proto[Reactor[R]] = {
      Reactor[R] { self =>
        f(self.main.serveBackpressure(medium, policy))
      }
    }
  }

  implicit class BackpressureSystemOps(
    val system: ReactorSystem
  ) {
    def genericBackpressureServer[R: Arrayable, T: Arrayable](
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T]
    )(f: Backpressure.Server[R, T] => Unit): Channel[R] = {
      val proto = Reactor.genericBackpressureServer(medium, policy)(f)
      system.spawn(proto)
    }

    def backpressureServer[R: Arrayable, T: Arrayable](
      medium: Backpressure.Medium[R, T],
      policy: Backpressure.Policy[T]
    )(f: Backpressure.PumpServer[R, T] => Unit): Channel[R] = {
      val proto = Reactor.backpressureServer(medium, policy)(f)
      system.spawn(proto)
    }
  }
}
