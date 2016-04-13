package io.reactors
package protocol






/** Communication patterns based on request-reply.
 */
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

    /** Request a stream of replies from the server channel.
     *
     *  The stream is interrupted when the server sends a `nil` event, as defined by the
     *  `Arrayable` type class for type `S`.
     *
     *  Server can reply with multiple events.
     *  There is no backpressure in this algorithm, so users are responsible for
     *  ensuring that the server does not flood the client.
     *  Furthermote, the client can at any point unsubscribe from the event
     *  stream without notifying the server, so ensuring that the server sends a finite
     *  stream is strongly recommended.
     *
     *  @param x     request event
     *  @return      an event stream with the server replies
     */
    def stream(x: T): Signal[S] = {
      val connector = Reactor.self.system.channels.open[S]
      val nil = implicitly[Arrayable[S]].nil
      val result = connector.events.takeWhile(_ != nil).toEmpty
      result.onDone(connector.seal())
      server ! ((x, connector.channel))
      result
    }
  }
}
