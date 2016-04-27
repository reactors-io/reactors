package io.reactors
package protocol






/** Communication patterns based on request-reply.
 */
trait ServerProtocols {
  /** A server channel accepts tuples with the request event and channel to reply on.
   */
  type Server[T, S] = Channel[Server.Req[T, S]]

  object Server {
    type Req[T, S] = (T, Channel[S])
  }

  implicit class ServerChannelBuilderOps(val builder: ReactorSystem.ChannelBuilder) {
    /** Open a new server channel.
     */
    def server[T, S]: Connector[(T, Channel[S])] = builder.open[(T, Channel[S])]
  }

  implicit class ServerSystemOps(val system: ReactorSystem) {
    /** Creates a server isolate.
     */
    def server[T, S](f: T => S): Server[T, S] = {
      system.spawn(Reactor[Server.Req[T, S]] { self =>
        self.main.events onMatch {
          case (x, ch) => ch ! f(x)
        }
      })
    }
  }

  implicit class ServerOps[T, @specialized(Int, Long, Double) S: Arrayable](
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

  implicit class ServerStreamOps[T, @specialized(Int, Long, Double) S: Arrayable](
    val server: Server[(T, S), S]
  ) {
    /** Request a stream of replies from the server channel.
     *
     *  The stream is interrupted when the server sends a `term` event.
     *
     *  Server can reply with multiple events.
     *  There is no backpressure in this algorithm, so users are responsible for
     *  ensuring that the server does not flood the client.
     *  Furthermote, the client can at any point unsubscribe from the event
     *  stream without notifying the server, so ensuring that the server sends a finite
     *  stream is strongly recommended.
     *
     *  @param x     request event
     *  @param term  termination event, server must use it to indicate the end of stream
     *  @return      a signal emitting the server replies
     */
    def streaming(x: T, term: S): Signal[S] = {
      val connector = Reactor.self.system.channels.open[S]
      val result = connector.events.takeWhile(_ != term).toEmpty
      result.onDone(connector.seal())
      server ! ((x, term), connector.channel)
      result.withSubscription(Subscription { connector.seal() })
    }
  }
}
