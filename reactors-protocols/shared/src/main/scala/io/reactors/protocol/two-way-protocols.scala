package io.reactors
package protocol






trait TwoWayProtocols {
  /** Represents the state of an established two-way connection.
   *
   * @tparam I              type of the incoming events
   * @tparam O              type of the outgoing events
   * @param output          the output channel, for outgoing events
   * @param input           the input event stream, for incoming events
   * @param subscription    subscription associated with this 2-way connection
   */
  case class TwoWay[I, O](
    output: Channel[O], input: Events[I], subscription: Subscription
  ) extends Connection[I] {
    /** Same as `input`, events provided by this connection.
     */
    def events = input

    /** Same as `output`, channel provided to write with this connection.
     */
    def channel = output
  }

  object TwoWay {
    case class Server[I, O](
      channel: io.reactors.protocol.Server[Channel[I], Channel[O]],
      connections: Events[TwoWay[O, I]],
      subscription: Subscription
    ) extends ServerSide[Req[I, O], TwoWay[O, I]]

    type Req[I, O] = io.reactors.protocol.Server.Req[Channel[I], Channel[O]]
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
    def serveTwoWay()(implicit a: Arrayable[O]): TwoWay.Server[I, O] = {
      val connections = connector.events map {
        case (outputChannel, reply) =>
          val system = Reactor.self.system
          val output = system.channels.daemon.open[O]
          reply ! output.channel
          TwoWay(outputChannel, output.events, Subscription(output.seal()))
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
    def connect()(implicit a: Arrayable[I]): IVar[TwoWay[I, O]] = {
      val system = Reactor.self.system
      val output = system.channels.daemon.open[I]
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
    )(implicit a: Arrayable[O]): Channel[TwoWay.Req[I, O]] = {
      system.spawn(Reactor[TwoWay.Req[I, O]] { self =>
        val server = self.main.serveTwoWay()
        server.connections.onEvent(twoWay => f(server, twoWay))
      })
    }
  }
}
