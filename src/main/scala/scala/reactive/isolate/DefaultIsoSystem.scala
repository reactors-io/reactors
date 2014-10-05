package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.collection._



/** Default isolate system implementation.
 *
 *  @param name      the name of this isolate system
 *  @param bundle    the scheduler bundle used by the isolate system
 */
class DefaultIsoSystem(val name: String, val bundle: IsoSystem.Bundle = IsoSystem.defaultBundle)
extends IsoSystem {
  private val isolates = mutable.Map[String, IsoFrame]()
  private var uniqueNameCount = new AtomicLong(0L)
  private val monitor = new util.Monitor

  protected def uniqueId(): Long = {
    val uid = uniqueNameCount.incrementAndGet()
    uid
  }

  protected def uniqueName(name: String) = if (name == null) {
    val uid = uniqueId()
    s"isolate-$uid"
  } else ensureUnique(name)

  private def ensureUnique(name: String): String = monitor.synchronized {
    if (isolates contains name) exception.illegalArg(s"isolate name '$name' already exists.")
    else name
  }

  protected[reactive] def releaseNames(name: String): Unit = monitor.synchronized {
    channels.removeIsolate(name)
    isolates.remove(name)
  }

  protected[reactive] def newChannel[@spec(Int, Long, Double) Q](reactor: Reactor[Q]) = {
    new Channel.Synced(reactor, new util.Monitor)
  }

  val channels = new IsoSystem.Channels.Default(this)

  def isolate[@spec(Int, Long, Double) T: Arrayable](proto: Proto[Iso[T]]): Channel[T] = {
    val isolate = createFrame(proto)
    val frame = isolate.frame
    monitor.synchronized {
      isolates(frame.name) = frame
    }
    frame.wake()
    isolate.channel
  }

}


object DefaultIsoSystem {

}

