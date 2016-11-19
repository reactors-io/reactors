package io.reactors
package protocol



import io.reactors.common.BinaryHeap
import io.reactors.common.UnrolledRing
import io.reactors.services.Channels



trait ReliableProtocols {
  /** Represents a connected reliable channel.
   *
   *  When using a reliable channel to send events, clients have some level of guarantee
   *  that their events will not be impaired in some way. The exact guarantees are
   *  detailed in the `Policy` object which must be specified when establishing a
   *  reliable connection.
   *
   *  To close this reliable connection, clients must use the associated subscription.
   *
   *  @tparam T                 type of the events sent by this reliable channel
   *  @param channel            channel underlying the reliable connection
   *  @param subscription       subscription associated with the reliable connection
   */
  case class Reliable[T](channel: Channel[T], subscription: Subscription)

  object Reliable {
    case class Connection[T](events: Events[T], subscription: Subscription)
    extends io.reactors.protocol.Connection[T]

    case class Server[T](
      channel: Channel[Req[T]],
      connections: Events[Reliable.Connection[T]],
      subscription: Subscription
    ) extends ServerSide[Req[T], Reliable.Connection[T]]

    type Req[T] = io.reactors.protocol.TwoWay.Req[Long, Stamp[T]]

    case class Policy[T](
      client: (Events[T], io.reactors.protocol.TwoWay[Long, Stamp[T]]) => Subscription,
      server: (io.reactors.protocol.TwoWay[Stamp[T], Long], Channel[T]) => Subscription
    )

    object Policy {
      /** Assumes that the transport may reorder and indefinitely delay some events.
       *
       *  Furthermore, the requirement is that the underlying medium is not lossy,
       *  and that it does not create duplicates.
       */
      def reorder[T: Arrayable](window: Int) = Policy[T](
        (sends, twoWay) => {
          var lastAck = 0L
          var lastStamp = 0L
          val queue = new UnrolledRing[T]
          val io.reactors.protocol.TwoWay(channel, acks, subscription) = twoWay
          sends onEvent { x =>
            if ((lastStamp - lastAck) < window) {
              lastStamp += 1
              channel ! Stamp.Some(x, lastStamp)
            } else {
              queue.enqueue(x)
            }
          }
          acks onEvent { stamp =>
            lastAck = math.max(lastAck, stamp)
            while (queue.nonEmpty && (lastStamp - lastAck) < window) {
              lastStamp += 1
              channel ! Stamp.Some(queue.dequeue(), lastStamp)
            }
          } andThen (channel ! Stamp.None())
        },
        (twoWay, deliver) => {
          val io.reactors.protocol.TwoWay(acks, events, subscription) = twoWay
          var nextStamp = 1L
          val queue = new BinaryHeap[Stamp[T]]()(
            implicitly,
            Order((x, y) => (x.stamp - y.stamp).toInt)
          )
          events onMatch {
            case stamp @ Stamp.Some(x, timestamp) =>
              if (timestamp == nextStamp) {
                acks ! nextStamp
                nextStamp += 1
                deliver ! x
                while (queue.nonEmpty && queue.head.stamp == nextStamp) {
                  val Stamp.Some(y, _) = queue.dequeue()
                  acks ! nextStamp
                  nextStamp += 1
                  deliver ! y
                }
              } else {
                queue.enqueue(stamp)
              }
          } andThen (acks ! -1)
        }
      )
    }

    object TwoWay {
      case class Server[I, O](
        channel: io.reactors.protocol.Server[
          Channel[Reliable.Req[I]],
          Channel[Reliable.Req[O]]
        ],
        connections: Events[TwoWay[O, I]],
        subscription: Subscription
      ) extends ServerSide[TwoWay.Req[I, O], TwoWay[O, I]]

      type Req[I, O] = io.reactors.protocol.Server.Req[
        Channel[Reliable.Req[I]],
        Channel[Reliable.Req[O]]
      ]

      case class Policy[I, O](
        input: Reliable.Policy[I],
        output: Reliable.Policy[O],
        inputGuard: Reliable.Server[I] => Unit,
        outputGuard: Reliable.Server[O] => Unit
      )

      object Policy {
        def reorder[I: Arrayable, O: Arrayable](window: Int) =
          Reliable.TwoWay.Policy[I, O](
            Reliable.Policy.reorder[I](window),
            Reliable.Policy.reorder[O](window),
            server => {},
            server => {}
          )
      }
    }

    /** Represents the channel of the `Reliable` object.
     */
    object ChannelTag extends Channels.Tag
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
    def serveReliable(
      policy: Reliable.Policy[T] = Reliable.Policy.reorder[T](128)
    ): Reliable.Server[T] = {
      val system = Reactor.self.system
      val twoWayServer = connector.serveTwoWay()
      val connections = twoWayServer.connections map {
        case twoWay @ TwoWay(_, events, _) =>
          val reliable =
            system.channels.template(Reliable.ChannelTag).daemon.shortcut.open[T]
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
      policy: Reliable.Policy[T] = Reliable.Policy.reorder[T](128)
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

  implicit class ReliableReactorCompanionOps(val reactor: Reactor.type) {
    def reliableServer[T: Arrayable](policy: Reliable.Policy[T])(
      f: (Reliable.Server[T], Reliable.Connection[T]) => Unit
    ): Proto[Reactor[Reliable.Req[T]]] = {
      Reactor[Reliable.Req[T]] { self =>
        val server = self.main.serveReliable(policy)
        server.connections.onEvent(connection => f(server, connection))
      }
    }
  }

  implicit class ReliableSystemOps(val system: ReactorSystem) {
    def reliableServer[T: Arrayable](policy: Reliable.Policy[T])(
      f: (Reliable.Server[T], Reliable.Connection[T]) => Unit
    ): Channel[Reliable.Req[T]] = {
      system.spawn(Reactor.reliableServer[T](policy)(f))
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
    def serveTwoWayReliable(
      policy: Reliable.TwoWay.Policy[I, O] = Reliable.TwoWay.Policy.reorder[I, O](128)
    ): Reliable.TwoWay.Server[I, O] = {
      val system = Reactor.self.system
      val connections = connector.events.map {
        case req @ (inServer, reply) =>
          val outServer = system.channels.daemon.shortcut.reliableServer[O]
            .serveReliable(policy.output)
          outServer.connections.on(outServer.subscription.unsubscribe())
          policy.outputGuard(outServer)
          reply ! outServer.channel

          val inReliable = inServer.openReliable(policy.input)

          (outServer.connections sync inReliable) { (out, in) =>
            TwoWay(in.channel, out.events, in.subscription.chain(out.subscription))
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
    val reliableServer: Channel[io.reactors.protocol.Reliable.TwoWay.Req[I, O]]
  ) {
    def connectReliable(
      policy: Reliable.TwoWay.Policy[I, O] = Reliable.TwoWay.Policy.reorder[I, O](128)
    ): IVar[TwoWay[I, O]] = {
      val system = Reactor.self.system
      val inServer = system.channels.daemon.shortcut.reliableServer[I]
        .serveReliable(policy.input)
      policy.inputGuard(inServer)
      inServer.connections.on(inServer.subscription.unsubscribe())

      (reliableServer ? inServer.channel).map { outServer =>
        val outReliable = outServer.openReliable(policy.output)

        (outReliable sync inServer.connections) { (out, in) =>
          TwoWay(out.channel, in.events, out.subscription.chain(in.subscription))
        }
      }.union.toIVar
    }
  }

  implicit class ReliableTwoWaySystemOps[I: Arrayable, O: Arrayable](
    val system: ReactorSystem
  ) {
    def reliableTwoWayServer(
      f: (Reliable.TwoWay.Server[I, O], TwoWay[O, I]) => Unit,
      policy: Reliable.TwoWay.Policy[I, O] = Reliable.TwoWay.Policy.reorder[I, O](128)
    ): Channel[Reliable.TwoWay.Req[I, O]] = {
      val proto = Reactor[Reliable.TwoWay.Req[I, O]] { self =>
        val server = self.main.serveTwoWayReliable(policy)
        server.connections.onEvent(twoWay => f(server, twoWay))
      }
      system.spawn(proto)
    }
  }
}
