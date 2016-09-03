package io.reactors



import scala.collection._
import io.reactors.concurrent.Frame



/** `Channel` is a shareable reference to a writing endpoint of a reactor.
 *
 *  By calling the channel's `!` method, an event is sent to the channel. The event is
 *  eventually emitted on the channel's corresponding event stream, which is readable
 *  only by the reactor that owns that channel.
 *
 *  @tparam T       type of the events propagated by this channel
 */
trait Channel[@spec(Int, Long, Double) T] {
  /** Sends a single event on this channel.
   *
   *  @param x      event sent to the channel
   */
  def !(x: T): Unit
}


/** Default channel implementations.
 */
object Channel {
  class Shared[@spec(Int, Long, Double) T: Arrayable](
    val url: ChannelUrl,
    @transient var underlying: Channel[T]
  ) extends Channel[T] with Serializable {
    private def resolve(): Unit = {
      val system = Reactor.self.system
      underlying = system.service[Remote].resolve[T](url)
    }

    def !(x: T): Unit = {
      // TODO: Make initialization thread-safe.
      if (underlying == null) resolve()
      underlying ! x
    }

    private[reactors] def asLocal: Channel.Local[T] = {
      underlying.asInstanceOf[Channel.Local[T]]
    }
  }

  class Zero[@spec(Int, Long, Double) T] extends Channel[T] {
    def !(x: T) = {}
  }

  class Failed[@spec(Int, Long, Double) T] extends Channel[T] {
    def !(x: T) = sys.error("Failed channel cannot deliver messages.")
  }

  class Local[@spec(Int, Long, Double) T](
    val system: ReactorSystem,
    val uid: Long,
    val frame: Frame,
    val shortcut: Boolean
  ) extends Channel[T] with Identifiable {
    private[reactors] var connector: Connector[T] = _
    private[reactors] var isOpen = true

    def !(x: T): Unit = {
      system.debugApi.eventSent(this, x)
      if (isOpen) {
        if (shortcut && (Reactor.currentFrame eq frame)) {
          connector.queue.bypass(x)
        } else frame.enqueueEvent(connector, x)
      }
    }

    def isSealed: Boolean = !isOpen
  }
}
