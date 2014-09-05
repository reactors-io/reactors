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

  protected[reactive] def releaseName(name: String): Unit = monitor.synchronized {
    isolates.remove(name)
  }

  protected[reactive] def newChannel[@spec(Int, Long, Double) Q](reactor: Reactor[Q]) = {
    new Channel.Synced(reactor, new util.Monitor)
  }

  def channels = new IsoSystem.Channels {
    private val channelMap = ReactMap[String, Channel[_]]
    def update(name: String, c: Channel[_]) = channelMap.synchronized {
      if (!channelMap.contains(name)) channelMap(name) = c
      else sys.error(s"Name $name already contained in channels.")
    }
    def apply(name: String): Channel[_] = channelMap.synchronized {
      channelMap(name)
    }
    def get(name: String): Ivar[Channel[_]] = ???
    def getUnsealed(name: String): Ivar[Channel[_]] = ???
  }

  def isolate[@spec(Int, Long, Double) T: Arrayable](proto: Proto[Iso[T]], name: String = null): Channel[T] = {
    val isolate = createFrame(proto, name)
    val frame = isolate.frame
    monitor.synchronized {
      isolates(frame.name) = frame
    }
    frame.wake()
    isolate.channel
  }

}
