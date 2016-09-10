package io.reactors
package protocol



import io.reactors.common.IndexedSet
import io.reactors.common.concurrent.UidGenerator
import io.reactors.container.RHashMap
import scala.collection._



/** Communication patterns based on backpressure.
 *
 *  Backpressure ensures that the backpressure server has a bound on the number of
 *  events in its event queue, at all times. This is achieved by preventing the clients
 *  from sending too many events.
 *
 *  Clients must ask the backpressure server for a backpressure link. When a client
 *  receives a link, it must check if it has sufficient budget to send events to
 *  the server, and potentially wait before sending an event. The budget is spent each
 *  time that the client sends an event, and replenished when the server sends a token.
 *
 *  There are several kinds of backpressure exposed by this module:
 *
 *  - The for-all backpressure policy maintains a fixed number of tokens across all
 *    backpressure links. The advantage is that the server cannot be overwhelmed,
 *    regardless of the number of clients. The disadvantage is that some clients can
 *    fail while holding some of the tokens, in which case tokens are lost. If failures
 *    are possible in the system, such scenarios can ultimately starve the protocol.
 *  - The per-client backpressure policy maintains a fixed number of tokens per each
 *    backpressure link. The advantage is that the failure of any single client only
 *    obliviates the tokens from the client's own backpressure links, so other clients
 *    cannot be starved. The disadvantage is that the total number of clients may be
 *    unbounded, which can overwhelm the backpressure server.
 */
trait BackpressureProtocols {
  self: ServerProtocols =>

  /** Augments reactor systems with operations used to create backpressure reactors.
   */
  implicit class BackpressureSystemOps(val system: ReactorSystem) {
    /** Creates and starts a for-all backpressure server reactor.
     *
     *  @see [[io.reactors.protocol.BackpressureProtocols]]
     *
     *  @tparam T       type of the events send over the backpressure link
     *  @param budget   the total number of events that can be in the queue
     *  @return         a backpressure server channel of the new reactor
     */
    def backpressureForAll[T: Arrayable](budget: Long)(
      f: Events[T] => Unit
    ): Backpressure.Server[T] =
      system.spawn(Reactor[Backpressure.Req[T]] { self =>
        self.main.extra[Backpressure.ChannelInfo[T]](
          new Backpressure.ChannelInfo(implicitly[Arrayable[T]]))
        f(self.main.pressureForAll(budget))
      })

    /** Creates and starts a per-client backpressure server reactor.
     *
     *  @see [[io.reactors.protocol.BackpressureProtocols]]
     *
     *  @tparam T       type of the events send over the backpressure link
     *  @param budget   the total number of events that can be in the queue
     *  @return         a backpressure server channel of the new reactor
     */
    def backpressurePerClient[T: Arrayable](budget: Long)(
      f: Events[T] => Unit
    ): Backpressure.Server[T] =
      system.spawn(Reactor[Backpressure.Req[T]] { self =>
        self.main.extra[Backpressure.ChannelInfo[T]](
          new Backpressure.ChannelInfo(implicitly[Arrayable[T]]))
        f(self.main.pressurePerClient(budget))
      })
  }

  /** Methods for creating backpressure channels.
   */
  implicit class BackpressureChannelBuilderOps(val builder: ChannelBuilder) {
    /** Creates a channel with the backpressure server type.
     *
     *  The channel itself does not have backpressure logic. To start the backpressure
     *  protocol, additional methods must be called on the resulting connector.
     *
     *  @tparam T      the type of events sent over the backpressure channel
     *  @return        a connector of the backpressure request type
     */
    def backpressure[T: Arrayable]: Connector[Backpressure.Req[T]] = {
      val info = new Backpressure.ChannelInfo(implicitly[Arrayable[T]])
      builder.extra(info).open[Backpressure.Req[T]]
    }
  }

  /** Methods for starting backpressure protocols on backpressure channels.
   */
  implicit class BackpressureConnectorOps[T](val conn: Connector[Backpressure.Req[T]]) {
    /** Starts the for-all protocol on the backpressure channel.
     *
     *  @see [[io.reactors.protocol.BackpressureProtocols]]
     */
    def pressureForAll(initialBudget: Long): Events[T] = {
      implicit val a = conn.extra[Backpressure.ChannelInfo[T]].arrayable
      val system = Reactor.self.system
      val uidGen = new UidGenerator(1)
      val input = system.channels.daemon.open[T]
      val links = new IndexedSet[Channel[Long]]
      val linkstate = new RHashMap[Backpressure.Uid, Backpressure.LinkState]
      val allTokens = system.channels.daemon.shortcut.router[Long]
        .route(Router.roundRobin(links))
      var budget = initialBudget
      input.events on {
        allTokens.channel ! 1L + budget
      }
      conn.events onMatch {
        case (Backpressure.Open(tokens), response) =>
          val uid = uidGen.generate()
          links += tokens.inject { num =>
            val s = linkstate.applyOrNil(uid)
            if (s != null) s.budget += num
          }
          linkstate(uid) = new Backpressure.LinkState(tokens)
          if (budget > 0) {
            allTokens.channel ! budget
            budget = 0
          }
          response ! (uid, input.channel)
        case (Backpressure.Seal(uid), _) =>
          val s = linkstate.applyOrNil(uid)
          if (s != null) {
            budget += linkstate(uid).budget
            linkstate.remove(uid)
          }
      }
      input.events
    }

    /** Starts the per-client protocol on the backpressure channel.
     *
     *  @see [[io.reactors.protocol.BackpressureProtocols]]
     */
    def pressurePerClient(initialBudget: Long): Events[T] = {
      implicit val a = conn.extra[Backpressure.ChannelInfo[T]].arrayable
      val system = Reactor.self.system
      val input = system.channels.daemon.open[T]
      conn.events onMatch {
        case (Backpressure.Open(tokens), response) =>
          val clientInput = system.channels.daemon.open[T]
          clientInput.events.pipe(input.channel)
          clientInput.events.on(tokens ! 1L)
          tokens ! initialBudget
          response ! (0L, clientInput.channel)
        case (Backpressure.Seal(_), _) =>
          // Safe to ignore the seal operation, as budget is not shared.
      }
      input.events
    }
  }

  implicit class BackpressureServerOps[T](val server: Backpressure.Server[T]) {
    /** Obtains the backpressure link from the backpressure server.
     *
     *  This method is called from the clients that can access the backpressure server.
     *  The methods returns a single-assignment variable through which the server
     *  responds with a backpressure link. Once obtained, the backpressure link can be
     *  used to send events to the server.
     *
     *  @see [[io.reactors.protocol.BackpressureProtocols]]
     */
    def link: IVar[Backpressure.Link[T]] = {
      val system = Reactor.self.system
      val tokens = system.channels.daemon.open[Long]
      val budget = RCell(0L)
      tokens.events.onEvent(budget := budget() + _)
      (server ? Backpressure.Open(tokens.channel)).map {
        case (uid, ch) =>
          Reactor.self.sysEvents onMatch {
            case ReactorTerminated => server ?! Backpressure.Seal(uid)
          }
          new Backpressure.Link(uid, ch, budget)
      }.toIVar
    }
  }
}


/** Contains backpressure types and auxiliary classes.
 */
object Backpressure {
  /** Unique identifier for established backpressure links.
   */
  type Uid = Long

  /** Type of the backpressure server channel.
   */
  type Server[T] = io.reactors.protocol.Server[Payload, (Uid, Channel[T])]

  /** Type of the backpressure request.
   */
  type Req[T] = io.reactors.protocol.Server.Req[Payload, (Uid, Channel[T])]

  /** Represents the state of the link between the client and the backpressure server.
   */
  class Link[T](
    private val uid: Long,
    private val channel: Channel[T],
    private val budget: RCell[Long]
  ) extends Serializable {
    /** A signal denoting whether event sends are available.
     *
     *  Clients can subscribe to this signal to execute operations once event sends
     *  become possible.
     */
    val available: Signal[Boolean] = budget.map(_ > 0).toSignal(false)

    /** Attempts to send an event to the server.
     *
     *  If successful, returns `true`. Otherwise, returns `false`.
     */
    def trySend(x: T): Boolean = {
      if (budget() == 0) false else {
        budget := budget() - 1
        channel ! x
        true
      }
    }
  }

  /** Holds extra info about that backpressure server channel.
   */
  class ChannelInfo[T](val arrayable: Arrayable[T])

  /** Holds state of the link.
   */
  class LinkState(tokens: Channel[Long]) {
    var budget = 0L
  }

  /** Payload for backpressure requests.
   */
  sealed trait Payload

  /** Payload for opening a backpressure link.
   */
  case class Open(ch: Channel[Long]) extends Payload

  /** Payload for closing an existing backpressure link.
   */
  case class Seal(uid: Uid) extends Payload
}
