package io.reactors
package protocol



import io.reactors.common.BinaryHeap
import io.reactors.common.UnrolledRing



trait ReliableProtocols {
  self: StandardProtocols =>

  case class Reliable[T](channel: Channel[T], subscription: Subscription)

  object Reliable {
    type Server[T] = io.reactors.protocol.TwoWay.Server[Stamp[T], Long]

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
          } and (channel ! Stamp.None())
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
          } and (acks ! -1)
        }
      )
    }

    object TwoWay {
      type Server[I, O] =
        io.reactors.protocol.Server[Reliable.Server[O], Reliable.Server[I]]

      type Req[I, O] =
        io.reactors.protocol.Server.Req[Reliable.Server[O], Reliable.Server[I]]
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
      f: (Events[T], Subscription) => Unit,
      policy: Reliable.Policy[T] = Reliable.Policy.ordered[T](128)
    ): Connector[Reliable.Req[T]] = {
      val system = Reactor.self.system
      connector.twoWayServe {
        case twoWay @ TwoWay(_, events, subscription) =>
          val reliable = system.channels.daemon.shortcut.open[T]
          val resources = policy.server(twoWay, reliable.channel)
          val connection = Subscription(reliable.seal())
            .chain(resources)
            .chain(subscription)
          events.collect({ case s @ Stamp.None() => s })
            .toIVar.on(connection.unsubscribe())
          f(reliable.events, connection)
      }
      connector
    }
  }

  implicit class ReliableServerOps[T: Arrayable](
    val server: Reliable.Server[T]
  ) {
    def openReliable(
      policy: Reliable.Policy[T] = Reliable.Policy.ordered[T](128)
    ): IVar[Reliable[T]] = {
      val system = Reactor.self.system
      server.connect() map {
        case twoWay @ TwoWay(_, acks, subscription) =>
          val reliable = system.channels.daemon.shortcut.open[T]
          val resources = policy.client(reliable.events, twoWay)
          val connection = Subscription(reliable.seal())
            .chain(resources)
            .chain(subscription)
          acks.filter(_ == -1).toIVar.on(connection.unsubscribe())
          Reliable(reliable.channel, connection)
      } toIVar
    }
  }

  implicit class ReliableSystemOps[T: Arrayable](val system: ReactorSystem) {
    def reliableServer(
      f: (Events[T], Subscription) => Unit,
      policy: Reliable.Policy[T] = Reliable.Policy.ordered[T](128)
    ): Channel[Reliable.Req[T]] = {
      system.spawn(Reactor[Reliable.Req[T]] { self =>
        self.main.reliableServe(f, policy)
      })
    }
  }

  /* Two-way reliable protocols */

  // implicit class ReliableTwoWayChannelBuilderOps(val builder: ChannelBuilder) {
  //   def reliableTwoWayServer[I, O]: Connector[Reliable.TwoWay.Req[I, O]] = {
  //     builder.open[Reliable.TwoWay.Req[I, O]]
  //   }
  // }

  // implicit class ReliableTwoWayConnectorOps[I, O](
  //   val connector: Connector[Reliable.TwoWay.Req[I, O]]
  // ) {
  //   def reliableTwoWayServe(
  //     f: TwoWay[O, I] => Unit,
  //     inputPolicy: Reliable.Policy[I] = Reliable.Policy.ordered[I],
  //     outputPolicy: Reliable.Policy[O] = Reliable.Policy.ordered[O]
  //   ): Connector[Reliable.TwoWay.Req[I, O]] = {
  //     val system = Reactor.self.system
  //     connector.events onEvent {
  //       case (outServer, reply) =>
  //         reply ! inServer
  //         val output = outServer.openReliable(outputPolicy)
  //         val input = out
  //         (output zip input) { (o, i) =>
  //           f(TwoWay(o, i))
  //         }
  //     }
  //     connector
  //   }
  // }
}
