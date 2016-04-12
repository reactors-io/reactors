package io.reactors
package protocol






trait ServerProtocols {
  /** A server channel accepts tuples with the request event and channel to reply on.
   */
  type Server[T, S] = Channel[(T, Channel[S])]

  implicit class ServerSystemOps(val system: ReactorSystem) {
    /** Open a new server channel.
     */
    def server[T, S](name: String = ""): Connector[(T, Channel[S])] =
      system.channels.named(name).open[(T, Channel[S])]
  }

  implicit class ServerChannelOps[T, @specialized(Int, Long, Double) S: Arrayable](
    val server: Server[T, S]
  ) {
    /** Request a single reply from the server channel.
     *
     *  Returns an `IVar` with the server response.
     *  If the server responds with multiple events, only the first event is returned.
     *
     *  @param x     request event
     *  @return      the single assignment variable with the reply
     */
    def ?(x: T): IVar[S] = {
      val connector = Reactor.self.system.channels.open[S]
      val result = connector.events.toIVar
      result.onDone(connector.seal())
      server ! ((x, connector.channel))
      result
    }
  }
}
