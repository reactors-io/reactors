package io.reactors
package protocol






/** Communication patterns based on backpressure.
 */
trait BackpressureProtocols {
  self: ServerProtocols =>

  object Backpressure {
    type Server[T] = self.Server[Channel[Long], Channel[T]]
    type Req[T] = self.Server.Req[Channel[Long], Channel[T]]

    class Link[T](
      private val channel: Channel[T],
      private val budget: Events[Long]
    ) extends Serializable {
      def available: Signal[Boolean] = ???
      def trySend(x: T): Boolean = ???
    }
  }

  implicit class BackpressureSystemOps(val system: ReactorSystem) {
    def backpressure[T](f: Events[T] => Unit): Backpressure.Server[T] =
      system.spawn(Reactor[Backpressure.Req[T]] { self =>
        f(self.main.pressurize().events)
      })
  }

  implicit class BackpressureChannelBuilderOps(val builder: ChannelBuilder) {
    def backpressure[T]: Connector[Backpressure.Req[T]] =
      builder.open[Backpressure.Req[T]]
  }

  implicit class BackpressureConnectorOps[T](val conn: Connector[Backpressure.Req[T]]) {
    def pressurize(): Events[T] = {
      ???
    }
  }

  implicit class BackpressureServerOps[T](val server: Backpressure.Server[T]) {
    def link: IVar[Backpressure.Link[T]] = {
      val system = Reactor.self.system
      val budget = system.channels.daemon.open[Long]
      (server ? budget.channel).map {
        ch => new Backpressure.Link(ch, budget.events)
      }.toIVar
    }
  }
}
