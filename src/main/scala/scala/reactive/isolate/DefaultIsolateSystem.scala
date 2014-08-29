package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.collection._



/** Default isolate system implementation.
 *
 *  @param name      the name of this isolate system
 *  @param bundle    the scheduler bundle used by the isolate system
 */
class DefaultIsolateSystem(val name: String, val bundle: IsolateSystem.Bundle = IsolateSystem.defaultBundle)
extends IsolateSystem {
  private val isolates = mutable.Map[String, IsolateFrame]()
  private var uniqueNameCount = new AtomicLong(0L)
  private val monitor = new util.Monitor

  protected def uniqueName(name: String) = if (name == null) {
    val uid = uniqueNameCount.incrementAndGet()
    s"isolate-$uid"
  } else ensureUnique(name)

  private def ensureUnique(name: String): String = monitor.synchronized {
    if (isolates contains name) exception.illegalArg(s"isolate name '$name' already exists.")
    else name
  }

  protected[reactive] def newChannel[@spec(Int, Long, Double) Q](reactor: Reactor[Q]) = {
    new Channel.Synced(reactor, new util.Monitor)
  }

  def isolate[@spec(Int, Long, Double) T: Arrayable](proto: Proto[Isolate[T]], name: String = null): Channel[T] = {
    val isolate = createFrame(proto, name)
    val frame = isolate.frame
    monitor.synchronized {
      isolates(frame.name) = frame
    }
    frame.wake()
    isolate.channel
  }

}
