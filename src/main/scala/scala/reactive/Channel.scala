package scala.reactive



import scala.collection._



/** The `Channel` is the writing end of an isolate.
 *
 *  Every isolate is associated with a channel.
 *  Unlike reactive values or signals, channels can be used
 *  in a thread-safe way -- channels can be shared between
 *  isolates and their methods called from any isolate.
 *
 *  Events are sent to the channel using the `!` method.
 *
 *  {{{
 *  val c: Channel[Int] = ???
 *  c ! 17
 *  }}}
 *
 *  After the `seal` method is called on the channel,
 *  no more events can be sent to it.
 *
 *  {{{
 *  c.seal()
 *  c ! 21 // this call will not send the event
 *  }}}
 *
 *  The isolate stops once its channel is sealed,
 *  and the previously received events are processed.
 *
 *  @tparam T        the type of events in this channel
 */
trait Channel[@spec(Int, Long, Double) T] {

  /** Sends a single event to this channel.
   *
   *  If the channel is already sealed, the event is simply discarded.
   *
   *  @param x       the event to send
   */
  def !(x: T): Unit

  /** Seals this channel.
   *
   *  Once a seal is successful, no more `!` calls will succeed.
   *
   *  This method can only be called by the isolate that owns the channel.
   *
   *  @return        this reactive channel
   */
  def seal(): Channel[T]

  /** Checks if this channel was sealed.
   *
   *  Returns `true` on the owner isolate immediately after `seal` is called.
   *  For other isolates, this method is *eventually consistent* -- it eventually
   *  returns `true`.
   *
   *  @return        `true` if the channel is sealed, `false` otherwise
   */
  def isSealed: Boolean

}


/** Channel implementations and factory methods.
 */
object Channel {

  /** A synchronized channel.
   *
   *  Basic channel implementation for use within a single machine.
   *
   *  @tparam T        type of the channel events
   *  @param reactor   the reactor notified of this channel's events,
   *                   which unreacts after the channel is sealed
   *  @param monitor   private monitor object used for synchronization
   */
  class Synced[@spec(Int, Long, Double) T]
    (val reactor: Reactor[T], val monitor: util.Monitor)
  extends Channel[T] {
    private var sealedChannel = false

    def !(x: T) = ???
    def seal(): Channel[T] = {
      monitor.synchronized { sealedChannel = true }
      checkTerminated()
      this
    }
    def isSealed = sealedChannel
    private[reactive] def checkTerminated() {
      if (isSealed) reactor.unreact()
    }
  }

}
