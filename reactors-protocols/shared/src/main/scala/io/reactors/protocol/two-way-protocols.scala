package io.reactors
package protocol






trait TwoWayProtocols {
  case class TwoWay[I, O](
    input: Channel[I], output: Events[O], subscription: Subscription
  )

  object TwoWay {
    case class Server[I, O](
      channel: io.reactors.protocol.Server[Channel[O], Channel[I]],
      requests: Events[TwoWay[O, I]],
      subscription: Subscription
    )

    type Req[I, O] = io.reactors.protocol.Server.Req[Channel[O], Channel[I]]
  }

  implicit class TwoWayChannelBuilderOps(val builder: ChannelBuilder) {
    def twoWayServer[
      @spec(Int, Long, Double) I, @spec(Int, Long, Double) O
    ]: Connector[TwoWay.Req[I, O]] = {
      builder.open[TwoWay.Req[I, O]]
    }
  }

  implicit class TwoWayConnectorOps[
    @spec(Int, Long, Double) I, @spec(Int, Long, Double) O
  ](val connector: Connector[TwoWay.Req[I, O]]) {
    def twoWayServe()(implicit i: Arrayable[I]): TwoWay.Server[I, O] = {
      val connections = connector.events map {
        case (outputChannel, reply) =>
          val system = Reactor.self.system
          val input = system.channels.daemon.open[I]
          reply ! input.channel
          val outIn = TwoWay(outputChannel, input.events, Subscription(input.seal()))
          outIn
      } toEmpty

      TwoWay.Server(
        connector.channel,
        connections,
        connections.chain(Subscription(connector.seal()))
      )
    }
  }

  implicit class TwoWayServerOps[
    @spec(Int, Long, Double) I, @spec(Int, Long, Double) O
  ](val twoWayServer: Channel[TwoWay.Req[I, O]]) {
    def connect()(implicit a: Arrayable[O]): IVar[TwoWay[I, O]] = {
      val system = Reactor.self.system
      val output = system.channels.daemon.open[O]
      val result: Events[TwoWay[I, O]] = (twoWayServer ? output.channel) map {
        inputChannel =>
        TwoWay(inputChannel, output.events, Subscription(output.seal()))
      }
      result.toIVar
    }
  }

  implicit class TwoWaySystemOps(val system: ReactorSystem) {
    def twoWayServer[@spec(Int, Long, Double) I, @spec(Int, Long, Double) O](
      f: (TwoWay.Server[I, O], TwoWay[O, I]) => Unit
    )(implicit i: Arrayable[I]): Channel[TwoWay.Req[I, O]] = {
      system.spawn(Reactor[TwoWay.Req[I, O]] { self =>
        val server = self.main.twoWayServe
        server.requests.onEvent(twoWay => f(server, twoWay))
      })
    }
  }
}
