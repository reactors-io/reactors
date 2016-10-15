package io.reactors
package protocol






trait TwoWayProtocols {
  type TwoWay[I, O] = (Channel[I], Events[O])

  object TwoWay {
    type Server[I, O] = io.reactors.protocol.Server[Channel[O], TwoWay[I, O]]

    type Req[I, O] = io.reactors.protocol.Server.Req[Channel[O], TwoWay[I, O]]
  }

  implicit class TwoWayChannelBuilderOps(val builder: ChannelBuilder) {
    def twoWayServer[
      @spec(Int, Long, Double) I,
      @spec(Int, Long, Double) O
    ]: Connector[TwoWay.Req[I, O]] = {
      ???
    }
  }

  implicit class TwoWayConnectorOps[
    @spec(Int, Long, Double) I,
    @spec(Int, Long, Double) O
  ](val conn: Connector[TwoWay.Req[I, O]]) {
    def serve(f: TwoWay[O, I] => Unit): TwoWay.Server[I, O] = {
      ???
    }
  }
}
