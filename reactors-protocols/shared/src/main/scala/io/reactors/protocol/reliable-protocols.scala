package io.reactors
package protocol



import io.reactors.common.BinaryHeap



trait ReliableProtocols {
  object Reliable {
    type Server[T] = io.reactors.protocol.TwoWay.Server[(T, Long), Long]

    type Req[T] = io.reactors.protocol.TwoWay.Req[(T, Long), Long]

    case class Policy[T](
      deliver: ((T, Long), Channel[T]) => Unit, resources: Subscription
    )

    object Policy {
      /** Assumes that the underlying medium may reorder events.
       *
       *  Furthermore, the requirement is that the underlying medium is not lossy,
       *  and that it does not create duplicates.
       */
      def nonOrdered[T]: io.reactors.protocol.TwoWay[Long, (T, Long)] => Policy[T] = {
        case TwoWay(acks, events, subscription) =>
          var latest = 0L
          val queue = new BinaryHeap[(T, Long)]()(
            implicitly,
            Order((x: (T, Long), y: (T, Long)) => (x._2 - y._2).toInt)
          )
          Policy((timestamped, reliableChannel) => {
            val (x, timestamp) = timestamped
            if (timestamp == latest) {
              acks ! latest
              latest += 1
              reliableChannel ! x
              while (queue.nonEmpty && queue.head._2 == latest) {
                latest += 1
                val (x, _) = queue.dequeue()
                reliableChannel ! x
              }
            }
          }, Subscription.empty)
      }
    }

    // type TwoWay[I, O] = io.reactors.protocol.TwoWay[(I, Long), (O, Long)]

    // object TwoWay {
    //   type Server[I, O] =
    //     io.reactors.protocol.Server[Reliable.Server[I], Reliable.Server[O]]

    //   type Req[I, O] =
    //     io.reactors.protocol.Server.Req[Reliable.Server[I], Reliable.Server[O]]
    // }
  }

  implicit class ReliableChannelBuilderOps(val builder: ChannelBuilder) {
    def reliable[@specialized(Int, Long, Double) T]: Connector[Reliable.Req[T]] = {
      builder.open[Reliable.Req[T]]
    }
  }

  implicit class ReliableConnectorOps[@spec(Int, Long, Double) T](
    val connector: Connector[Reliable.Req[T]]
  ) {
    def rely(
      f: (Events[T], Subscription) => Unit,
      newPolicy: TwoWay[Long, (T, Long)] => Reliable.Policy[T] =
        Reliable.Policy.nonOrdered[T]
    )(implicit a: Arrayable[T]): Subscription = {
      val system = Reactor.self.system
      connector.twoWayServe {
        case twoWay @ TwoWay(acks, events, subscription) =>
          val reliable = system.channels.daemon.shortcut.open[T]
          val policy = newPolicy(twoWay)
          events onEvent { timestamped =>
            policy.deliver(timestamped, reliable.channel)
          }
          val close = Subscription(acks ! -1)
            .and(reliable.seal())
            .chain(policy.resources)
            .chain(subscription)
          f(reliable.events, close)
      }
      Subscription(connector.seal())
    }
  }

  // implicit class ReliableTwoWayChannelBuilderOps(val builder: ChannelBuilder) {
  //   def reliableTwoWayServer[I, O]: Connector[Reliable.TwoWay.Req[I, O]] = {
  //     ???
  //   }
  // }

  // implicit class ReliableTwoWayConnectorOps[
  //   @spec(Int, Long, Double) I,
  //   @spec(Int, Long, Double) O
  // ](val connector: Connector[Reliable.TwoWay.Req[I, O]]) {
  //   def rely(window: Int, f: Reliable.TwoWay[O, I] => Unit): Reliable.TwoWay[I, O] =
  //     ???
  // }
}
