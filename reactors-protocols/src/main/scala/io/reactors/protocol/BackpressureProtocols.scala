package io.reactors
package protocol



import scala.collection._



/** Communication patterns based on backpressure.
 */
trait BackpressureProtocols {
  self: ServerProtocols =>

  object Backpressure {
    type Server[T] = self.Server[Channel[Long], Channel[T]]
    type Req[T] = self.Server.Req[Channel[Long], Channel[T]]

    class Link[T](
      private val channel: Channel[T],
      private val tokens: Events[Long]
    ) extends Serializable {
      private val budget = RCell(0L)
      tokens.onEvent(budget := budget() + _)
      def available: Signal[Boolean] = budget.map(_ > 0).toSignal(false)
      def trySend(x: T): Boolean = {
        if (budget() == 0) false else {
          budget := budget() - 1
          channel ! x
          true
        }
      }
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
    def global(startingBudget: Long): Events[T] = {
      var budget = startingBudget
      val input = Reactor.self.system.channels.daemon.open[T]
      val links = ???
      conn.events onMatch {
        case (tokens, response) =>
          tokens ! ???
          response ! input.channel
      }
      input.events
    }
    def perClient(startingBudget: Long): Events[T] = {
      val system = Reactor.self.system
      val input = system.channels.daemon.open[T]
      conn.events onMatch {
        case (tokens, response) =>
          var budget = 0
          val clientInput = system.channels.daemon.open[T]
          clientInput.events.pipe(input.channel)
          clientInput.events.on(tokens ! 1L)
          tokens ! startingBudget
          response ! clientInput.channel
      }
      input.events
    }
  }

  implicit class BackpressureServerOps[T](val server: Backpressure.Server[T]) {
    def link: IVar[Backpressure.Link[T]] = {
      val system = Reactor.self.system
      val tokens = system.channels.daemon.open[Long]
      (server ? tokens.channel).map {
        ch => new Backpressure.Link(ch, tokens.events)
      }.toIVar
    }
  }
}
