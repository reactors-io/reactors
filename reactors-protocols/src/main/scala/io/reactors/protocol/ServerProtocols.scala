package io.reactors
package protocol






/** Communication patterns based on request-reply.
 */
trait ServerProtocols {
  self: Patterns =>

  /** A server channel accepts tuples with the request event and channel to reply on.
   */
  type Server[T, S] = Channel[Server.Req[T, S]]

  implicit class ServerChannelBuilderOps(val builder: ChannelBuilder) {
    /** Open a new server channel.
     *
     *  The returned connector only has the server type, but will not serve incoming
     *  requests - for this, `serve` must be called on the connector.
     */
    def server[T, S]: Connector[Server.Req[T, S]] = builder.open[Server.Req[T, S]]
  }

  implicit class ServerConnectorOps[T, S](val conn: Connector[Server.Req[T, S]]) {
    /** Installs a serving function to the specified connector.
     *
     *  Takes an optional stopping predicate that must return `true` for requests that
     *  seal the connector.
     */
    def serve(f: T => S)(
      implicit stop: T => Boolean = (req: T) => false
    ): Connector[Server.Req[T, S]] = {
      conn.events onMatch {
        case (x, ch) =>
          if (stop(x)) {
            conn.seal()
          } else {
            ch ! f(x)
          }
      }
      conn
    }
  }

  implicit class ServerSystemOps(val system: ReactorSystem) {
    /** Creates a server reactor.
     *
     *  This reactor always responds by mapping the request of type `T` into a response
     *  of type `S`, with the specified function `f`.
     *
     *  If the optional `stop` predicate returns `true` for some request, the reactor's
     *  main channel gets sealed.
     */
    def server[T, S](f: T => S)(
      implicit stop: T => Boolean = (req: T) => false
    ): Server[T, S] = {
      system.spawn(Reactor[Server.Req[T, S]](_.main.serve(f)))
    }

    /** Creates a server reactor that optionally responds to requests.
     *
     *  This reactor uses the function `f` to create a response of type `S` from the
     *  request type `T`. If the value obtained this way is not `nil`, the server
     *  responds.
     *
     *  If the optional `stop` predicate returns `true` for some request, the reactor's
     *  main channel gets sealed.
     */
    def maybeServer[T, S](f: T => S, nil: S)(
      implicit stop: T => Boolean = (req: T) => false
    ): Server[T, S] = {
      system.spawn(Reactor[Server.Req[T, S]] { self =>
        self.main.events onMatch {
          case (x, ch) =>
            if (stop(x)) {
              self.main.seal()
            } else {
              val resp = f(x)
              if (resp != nil) ch ! resp
            }
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


object Server {
  type Req[T, S] = (T, Channel[S])
}
