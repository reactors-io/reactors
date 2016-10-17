package io.reactors
package protocol






trait TwoWayProtocols {
  case class TwoWay[I, O](
    input: Channel[I], output: Events[O], subscription: Subscription
  )

  object TwoWay {
    type Server[I, O] = io.reactors.protocol.Server[Channel[O], Channel[I]]

    type Req[I, O] = io.reactors.protocol.Server.Req[Channel[O], Channel[I]]
  }

  implicit class TwoWayChannelBuilderOps(val builder: ChannelBuilder) {
    def twoWayServer[
      @spec(Int, Long, Double) I,
      @spec(Int, Long, Double) O
    ]: Connector[TwoWay.Req[I, O]] = {
      builder.open[TwoWay.Req[I, O]]
    }
  }

  implicit class TwoWayConnectorOps[
    @spec(Int, Long, Double) I,
    @spec(Int, Long, Double) O
  ](val conn: Connector[TwoWay.Req[I, O]]) {
    def twoWayServe(f: TwoWay[O, I] => Unit)(
      implicit i: Arrayable[I]
    ): Connector[TwoWay.Req[I, O]] = {
      conn.events onEvent {
        case (outputChannel, reply) =>
          val system = Reactor.self.system
          val input = system.channels.daemon.open[I]
          reply ! input.channel
          val outIn = TwoWay(outputChannel, input.events, Subscription(input.seal()))
          f(outIn)
      }
      conn
    }
  }

  implicit class TwoWayServerOps[
    @spec(Int, Long, Double) I,
    @spec(Int, Long, Double) O
  ](val twoWayServer: TwoWay.Server[I, O]) {
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
      f: TwoWay[O, I] => Unit
    )(implicit i: Arrayable[I]): Channel[TwoWay.Req[I, O]] = {
      system.spawn(Reactor[TwoWay.Req[I, O]] { self =>
        self.main.twoWayServe(f)
      })
    }
  }
}
