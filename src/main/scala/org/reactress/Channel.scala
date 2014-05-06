package org.reactress



import scala.collection._



/** The `Channel` is the writing end of an isolate.
 *
 *  Every isolate is associated with a channel.
 *  Unlike reactive values or signals, channels can be used
 *  in a thread-safe way -- channels can be shared between
 *  isolates and their methods called from any isolate.
 *
 *  Events cannot be directly sent to a channel.
 *  Instead, reactive values can be attached to channels.
 *  All events that a reactive value produces from that point
 *  end up in the channel.
 *
 *  {{{
 *  val r = new Reactive.Emitter[Int]
 *  c.attach(r)
 *  }}}
 *
 *  Once the `seal` method is called on the channel,
 *  no more reactive values can be attached to it.
 *
 *  {{{
 *  c.seal()
 *  c.attach(r) // this call will not attach a reactive
 *  }}}
 *
 *  The isolate stops once its channel is sealed,
 *  and all the attached reactive values unreact.
 *  This indicates no more events will arrive to the isolate.
 *
 *  @tparam T        the type of events in this channel
 */
trait Channel[@spec(Int, Long, Double) T] {

  /** Attaches a reactive value to this channel.
   *  
   *  This call has no effect if the reactive is already sealed.
   *
   *  @param r       the reactive to attach
   *  @return        this reactive channel, to chain attach calls or call `seal`
   */
  def attach(r: Reactive[T]): Channel[T]

  /** Seals this channel.
   *
   *  Once a seal is successful, no more `attach` calls will succeed.
   */
  def seal(): Channel[T]

  /** Composes this channel with a custom mapping function for the input events.
   *  
   *  Events from reactives passed to this channel are mapped inside their isolates.
   *
   *  @tparam S      type of the events the new channel will accept
   *  @param f       maps events in the resulting channel to events of the original channel
   *  @return        the new channel accepting events of type `S`
   */
  def compose[@spec(Int, Long, Double) S](f: S => T) = new Channel.Composed(this, f)

}


object Channel {

  private[reactress] class Composed[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Channel[T], val f: S => T)
  extends Channel[S] {
    def attach(r: Reactive[S]): Channel[S] = {
      self.attach(r.map(f))
      this
    }
    def seal(): Channel[S] = {
      self.seal()
      this
    }
  }

  class Synced[@spec(Int, Long, Double) T](val reactor: Reactor[T], val monitor: util.Monitor)
  extends Channel[T] {
    private var sealedChannel = false
    private val reactives = mutable.Map[Reactive[T], Reactive.Subscription]()
    def attach(r: Reactive[T]) = monitor.synchronized {
      if (!sealedChannel) {
        if (!reactives.contains(r)) reactives(r) = r.onReaction(new Reactor[T] {
          def react(event: T) = reactor.react(event)
          def unreact() {
            monitor.synchronized { reactives.remove(r) }
            checkTerminated()
          }
        })
      }
      this
    }
    def seal(): Channel[T] = {
      monitor.synchronized { sealedChannel = true }
      checkTerminated()
      this
    }
    private[reactress] def checkTerminated() {
      val done = monitor.synchronized { sealedChannel && reactives.isEmpty }
      if (done) reactor.unreact()
    }
  }

}
