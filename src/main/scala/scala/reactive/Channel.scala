package scala.reactive



import scala.collection._
import scala.reactive.isolate.Frame
import scala.reactive.remoting.ChannelUrl
import scala.reactive.remoting.Remoting



trait Channel[@spec(Int, Long, Double) T] {
  def !(x: T): Unit
}


object Channel {
  class Shared[@spec(Int, Long, Double) T: Arrayable](
    val url: ChannelUrl,
    @transient var underlying: Channel[T]
  ) extends Channel[T] with Serializable {
    private def resolve(): Unit = {
      val system = Iso.self.system
      if (system.bundle.urls.contains(url.isoUrl.systemUrl)) {
        system.channels.find[T](url.channelName) match {
          case Some(ch) => underlying = ch
          case None => underlying = new Failed
        }
      } else {
        underlying = system.service[Remoting].resolve[T](url)
      }
    }

    def !(x: T): Unit = {
      if (underlying == null) resolve()
      underlying ! x
    }

    private[reactive] def asLocal: Channel.Local[T] = {
      underlying.asInstanceOf[Channel.Local[T]]
    }
  }

  class Failed[@spec(Int, Long, Double) T] extends Channel[T] {
    def !(x: T) = ???
  }

  class Local[@spec(Int, Long, Double) T](
    val uid: Long,
    val queue: EventQueue[T],
    val frame: Frame
  ) extends Channel[T] with Identifiable {
    private[reactive] var isOpen = true

    def !(x: T): Unit = if (isOpen) frame.enqueueEvent(uid, queue, x)

    def isSealed: Boolean = !isOpen
  }
}
