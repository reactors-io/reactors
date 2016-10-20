package io.reactors
package protocol



import io.reactors.common.BinaryHeap
import io.reactors.common.UnrolledRing



trait ReliableProtocols {
  self: StandardProtocols =>

  case class Reliable[T](channel: Channel[T], subscription: Subscription)

  object Reliable {
    case class Connection[T](events: Events[T], subscription: Subscription)

    case class Server[T](
      channel: Channel[io.reactors.protocol.TwoWay.Req[Stamp[T], Long]],
      connections: Events[Reliable.Connection[T]],
      subscription: Subscription
    )

    type Req[T] = io.reactors.protocol.TwoWay.Req[Stamp[T], Long]

    case class Policy[T](
      client: (Events[T], io.reactors.protocol.TwoWay[Stamp[T], Long]) => Subscription,
      server: (io.reactors.protocol.TwoWay[Long, Stamp[T]], Channel[T]) => Subscription
    )

    object Policy {
      /** Assumes that the underlying medium may reorder events.
       *
       *  Furthermore, the requirement is that the underlying medium is not lossy,
       *  and that it does not create duplicates.
       */
      def ordered[T: Arrayable](window: Int) = Policy[T](
        (sends, twoWay) => {
          var lastAck = 0L
          var latest = 0L
          val queue = new UnrolledRing[T]
          val io.reactors.protocol.TwoWay(channel, acks, subscription) = twoWay
          sends onEvent { x =>
            if ((latest - lastAck) < window) {
              channel ! Stamp.Some(x, latest)
              latest += 1
            } else {
              queue.enqueue(x)
            }
          }
          acks onEvent { stamp =>
            lastAck = math.max(lastAck, stamp)
            while (queue.nonEmpty && (latest - lastAck) < window) {
              channel ! Stamp.Some(queue.dequeue(), latest)
              latest += 1
            }
          } andThen (channel ! Stamp.None())
        },
        (twoWay, deliver) => {
          val io.reactors.protocol.TwoWay(acks, events, subscription) = twoWay
          var latest = 0L
          val queue = new BinaryHeap[Stamp[T]]()(
            implicitly,
            Order((x, y) => (x.stamp - y.stamp).toInt)
          )
          events onMatch {
            case Stamp.Some(x, timestamp) =>
              if (timestamp == latest) {
                acks ! latest
                latest += 1
                deliver ! x
                while (queue.nonEmpty && queue.head.stamp == latest) {
                  val Stamp.Some(y, _) = queue.dequeue()
                  acks ! latest
                  latest += 1
                  deliver ! y
                }
              }
          } andThen (acks ! -1)
        }
      )
    }

    object TwoWay {
      case class Server[I, O](
        channel: io.reactors.protocol.Server[
          Channel[Reliable.Req[O]],
          Channel[Reliable.Req[I]]
        ],
        connections: Events[TwoWay[O, I]],
        subscription: Subscription
      )

      type Req[I, O] = io.reactors.protocol.Server.Req[
        Channel[Reliable.Req[O]],
        Channel[Reliable.Req[I]]
      ]

      case class Policy[I, O](
        input: Reliable.Policy[I],
        output: Reliable.Policy[O],
        inputGuard: Reliable.Server[I] => Unit,
        outputGuard: Reliable.Server[O] => Unit
      )

      object Policy {
        def ordered[I: Arrayable, O: Arrayable](window: Int) =
          Reliable.TwoWay.Policy[I, O](
            Reliable.Policy.ordered[I](window),
            Reliable.Policy.ordered[O](window),
            server => {},
            server => {}
          )
      }
    }
  }

  /* One-way reliable protocols */

  implicit class ReliableChannelBuilderOps(val builder: ChannelBuilder) {
    def reliableServer[T]: Connector[Reliable.Req[T]] = {
      builder.open[Reliable.Req[T]]
    }
  }

  implicit class ReliableConnectorOps[T: Arrayable](
    val connector: Connector[Reliable.Req[T]]
  ) {
    def reliableServe(
      policy: Reliable.Policy[T] = Reliable.Policy.ordered[T](128)
    ): Reliable.Server[T] = {
      val system = Reactor.self.system
      val twoWayServer = connector.twoWayServe
      val connections = twoWayServer.connections map {
        case twoWay @ TwoWay(_, events, _) =>
          val reliable = system.channels.daemon.shortcut.open[T]
          val resources = policy.server(twoWay, reliable.channel)
          val subscription = Subscription(reliable.seal())
            .chain(resources)
            .chain(twoWay.subscription)
          events.collect({ case s @ Stamp.None() => s })
            .toIVar.on(subscription.unsubscribe())
          Reliable.Connection(reliable.events, subscription)
      } toEmpty

      Reliable.Server(
        connector.channel,
        connections,
        connections.chain(twoWayServer.subscription).andThen(connector.seal())
      )
    }
  }

  implicit class ReliableServerOps[T: Arrayable](
    val server: Channel[Reliable.Req[T]]
  ) {
    def openReliable(
      policy: Reliable.Policy[T] = Reliable.Policy.ordered[T](128)
    ): IVar[Reliable[T]] = {
      val system = Reactor.self.system
      server.connect() map {
        case twoWay @ TwoWay(_, acks, _) =>
          val reliable = system.channels.daemon.shortcut.open[T]
          val resources = policy.client(reliable.events, twoWay)
          val subscription = Subscription(reliable.seal())
            .chain(resources)
            .chain(twoWay.subscription)
          acks.filter(_ == -1).toIVar.on(subscription.unsubscribe())
          Reliable(reliable.channel, subscription)
      } toIVar
    }
  }

  implicit class ReliableSystemOps[T: Arrayable](val system: ReactorSystem) {
    def reliableServer(
      f: (Reliable.Server[T], Reliable.Connection[T]) => Unit,
      policy: Reliable.Policy[T] = Reliable.Policy.ordered[T](128)
    ): Channel[Reliable.Req[T]] = {
      system.spawn(Reactor[Reliable.Req[T]] { self =>
        val server = self.main.reliableServe(policy)
        server.connections.onEvent(connection => f(server, connection))
      })
    }
  }

  /* Two-way reliable protocols */

  implicit class ReliableTwoWayChannelBuilderOps(val builder: ChannelBuilder) {
    def reliableTwoWayServer[I, O]: Connector[Reliable.TwoWay.Req[I, O]] = {
      builder.open[Reliable.TwoWay.Req[I, O]]
    }
  }

  implicit class ReliableTwoWayConnectorOps[I: Arrayable, O: Arrayable](
    val connector: Connector[Reliable.TwoWay.Req[I, O]]
  ) {
    def reliableTwoWayServe(
      policy: Reliable.TwoWay.Policy[I, O] = Reliable.TwoWay.Policy.ordered[I, O](128)
    ): Reliable.TwoWay.Server[I, O] = {
      val system = Reactor.self.system
      val connections = connector.events.map {
        case req @ (outServer, reply) =>
          val inServer = system.channels.daemon.shortcut.reliableServer[I]
            .reliableServe(policy.input)
          reply ! inServer.channel
          inServer.connections.on(inServer.subscription.unsubscribe())
          policy.inputGuard(inServer)

          val outReliable = outServer.openReliable(policy.output)

          (inServer.connections sync outReliable) { (in, out) =>
            TwoWay(out.channel, in.events, out.subscription.chain(in.subscription))
          } toIVar
      }.union.toEmpty

      Reliable.TwoWay.Server(
        connector.channel,
        connections,
        connections.andThen(connector.seal())
      )
    }
  }

  implicit class ReliableTwoWayServerOps[I: Arrayable, O: Arrayable](
    val reliable: Channel[Reliable.TwoWay.Req[I, O]]
  ) {
    def connectReliable(
      policy: Reliable.TwoWay.Policy[I, O] = Reliable.TwoWay.Policy.ordered[I, O](128)
    ): IVar[TwoWay[I, O]] = {
      val system = Reactor.self.system
      val outServer = system.channels.daemon.shortcut.reliableServer[O]
        .reliableServe(policy.output)
      policy.outputGuard(outServer)

      (reliable ? outServer.channel) map { inServer =>
        inServer.openReliable(policy.input)

        ???
      }

      ???
    }
  }
}
