package org.reactors



import scala.collection._
//import org.reactors.remoting.ChannelUrl
//import org.reactors.remoting.Remoting



/** `Channel` is a shareable reference to a writing endpoint of an isolate.
 *
 *  By calling the channel's `!` method, an event is sent to the channel. The event is
 *  eventually emitted on the channel's corresponding event stream, which is readable
 *  only by the isolate that owns that channel.
 */
trait Channel[@spec(Int, Long, Double) T] {
  def !(x: T): Unit
}


/** Default channel implementations.
 */
object Channel {
  // class Shared[@spec(Int, Long, Double) T: Arrayable](
  //   val url: ChannelUrl,
  //   @transient var underlying: Channel[T]
  // ) extends Channel[T] with Serializable {
  //   private def resolve(): Unit = {
  //     val system = Iso.self.system
  //     if (system.bundle.urls.contains(url.isoUrl.systemUrl)) {
  //       system.channels.find[T](url.channelName) match {
  //         case Some(ch) => underlying = ch
  //         case None => underlying = new Failed
  //       }
  //     } else {
  //       underlying = system.service[Remoting].resolve[T](url)
  //     }
  //   }

  //   def !(x: T): Unit = {
  //     if (underlying == null) resolve()
  //     underlying ! x
  //   }

  //   private[reactors] def asLocal: Channel.Local[T] = {
  //     underlying.asInstanceOf[Channel.Local[T]]
  //   }
  // }

  // class Failed[@spec(Int, Long, Double) T] extends Channel[T] {
  //   def !(x: T) = sys.error("Failed channel cannot deliver messages.")
  // }

  // class Local[@spec(Int, Long, Double) T](
  //   val uid: Long,
  //   val queue: EventQueue[T],
  //   val frame: Frame
  // ) extends Channel[T] with Identifiable {
  //   private[reactors] var isOpen = true

  //   def !(x: T): Unit = if (isOpen) frame.enqueueEvent(uid, queue, x)

  //   def isSealed: Boolean = !isOpen
  // }
}
