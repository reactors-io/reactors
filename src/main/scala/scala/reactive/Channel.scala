package scala.reactive



import scala.collection._
import scala.reactive.isolate.Frame
import scala.reactive.remoting.ChannelUrl
import scala.reactive.remoting.Remoting



trait Channel[@spec(Int, Long, Double) T] extends Identifiable {
  def !(x: T): Unit
}


object Channel {
  class Shared[@spec(Int, Long, Double) T](
    val uid: Long,
    val url: ChannelUrl,
    @transient var underlying: Channel[T]
  ) extends Channel[T] with Serializable {
    private def resolve(): Unit = {
      val system = Iso.self.system
      if (system.bundle.url == url.isoUrl.systemUrl) {
        system.channels.find[T](url.channelName) match {
          case Some(ch) => underlying = ch
          case None => underlying = new Failed(uid)
        }
      } else {
        underlying = system.service[Remoting].resolve(url)
      }
    }

    def !(x: T): Unit = {
      if (underlying == null) resolve()
      underlying ! x
    }

    def isSealed: Boolean = {
      if (underlying == null) resolve()
      underlying.isSealed
    }

    private[reactive] def asLocal: Channel.Local[T] = {
      underlying.asInstanceOf[Channel.Local[T]]
    }
  }

  class Failed[@spec(Int, Long, Double) T](
    val uid: Long
  ) extends Channel[T] {
    def !(x: T) = ???

    def isSealed: Boolean = ???
  }

  class Local[@spec(Int, Long, Double) T](
    val uid: Long,
    val queue: EventQueue[T],
    val frame: Frame
  ) extends Channel[T] {
    private[reactive] var isOpen = true

    def !(x: T): Unit = if (isOpen) frame.enqueueEvent(uid, queue, x)

    def isSealed: Boolean = !isOpen
  }
}
