package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.collection._



/** Default isolate system implementation.
 *
 *  @param name      the name of this isolate system
 *  @param bundle    the scheduler bundle used by the isolate system
 */
class DefaultIsolateSystem(val name: String, val bundle: Scheduler.Bundle = Scheduler.defaultBundle)
extends IsolateSystem {
  private val isolates = mutable.Map[String, IsolateFrame[_]]()
  private var uniqueNameCount = new AtomicLong(0L)
  private val monitor = new util.Monitor

  protected def uniqueName(name: String) = if (name == null) {
    val uid = uniqueNameCount.incrementAndGet()
    s"isolate-$uid"
  } else ensureUnique(name)

  def ensureUnique(name: String): String = monitor.synchronized {
    if (isolates contains name) exception.illegalArg(s"isolate name '$name' already exists.")
    else name
  }

  def isolate[@spec(Int, Long, Double) T: Arrayable](proto: Proto[Isolate[T]], name: String = null): Channel[T] = {
    val frame = createFrame(proto, name)
    val channel = frame.channel
    monitor.synchronized {
      isolates(frame.name) = frame
    }
    frame.wake()
    channel
  }

}
