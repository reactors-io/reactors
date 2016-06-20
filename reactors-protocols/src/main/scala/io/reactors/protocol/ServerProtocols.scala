package io.reactors
package protocol






/** Communication patterns based on request-reply.
 */
trait ServerProtocols {
  self: Patterns =>

  /** A server channel accepts tuples with the request event and channel to reply on.
   */
  type Server[T, S] = Channel[Server.Req[T, S]]

  object Server {
    type Req[T, S] = (T, Channel[S])

    /** Typeclass that describes common server options
     */
    abstract class Opts[+T] {
      def term: Option[T]
    }

    /** Options for which the server does not terminate.
     */
    object NoTerm extends Opts[Nothing] {
      def term = None
    }

    object Opts {
      def apply[T](x: T): Opts[T] = new Opts[T] {
        val term = Some(x)
      }
    }
  }

  implicit class ServerChannelBuilderOps(val builder: ChannelBuilder) {
    /** Open a new server channel.
     */
    def server[T, S]: Connector[(T, Channel[S])] = builder.open[(T, Channel[S])]
  }

  implicit class ServerSystemOps(val system: ReactorSystem) {
    /** Creates a server isolate.
     *
     *  This isolate always responds by mapping the request of type `T` into a response
     *  of type `S`, with the specified function `f`.
     *
     *  If `opts` parameter has a non-`None` terminator and the server receives that
     *  termination value, it will seal its main channel.
     */
    def server[T, S](f: T => S)(
      implicit opts: Server.Opts[T] = Server.NoTerm
    ): Server[T, S] = {
      system.spawn(Reactor[Server.Req[T, S]] { self =>
        self.main.events onMatch {
          case (x, ch) =>
            if (opts.term != None && opts.term.get == x) {
              self.main.seal()
            } else {
              ch ! f(x)
            }
        }
      })
    }

    /** Creates a server isolate that optionally responds to requests.
     *
     *  This isolate uses the function `f` to create a response of type `S` from the
     *  request type `T`. If the value obtained this way is not `nil`, the server
     *  responds.
     *
     *  If `opts` parameter has a non-`None` terminator and the server receives that
     *  termination value, it will seal its main channel.
     */
    def maybeServer[T, S](f: T => S, nil: S)(
      implicit opts: Server.Opts[T] = Server.NoTerm
    ): Server[T, S] = {
      system.spawn(Reactor[Server.Req[T, S]] { self =>
        self.main.events onMatch {
          case (x, ch) =>
            if (opts.term != None && opts.term.get == x) {
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
