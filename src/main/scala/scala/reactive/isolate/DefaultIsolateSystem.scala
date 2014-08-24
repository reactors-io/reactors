package scala.reactive
package isolate



import java.util.concurrent.atomic.AtomicReference
import scala.collection._



/** Default isolate system implementation.
 *
 *  @param name      the name of this isolate system
 */
class DefaultIsolateSystem(val name: String, val bundle: Scheduler.Bundle = Scheduler.defaultBundle)
extends IsolateSystem {
  private val isolates = mutable.Map[String, IsolateFrame[_]]()
  private var uniqueNameCount = 0L
  private val monitor = new util.Monitor

  protected def uniqueName() = {
    uniqueNameCount += 1
    s"isolate-$uniqueNameCount"
  } 

  def ensureUnique(name: String): String = {
    if (isolates contains name) exception.illegalArg(s"isolate name '$name' already exists.")
    else name
  }

  private def createAndResetIsolate[T](proto: Proto[Isolate[T]]): Isolate[T] = {
    val oldi = Isolate.selfIsolate.get
    try {
      proto.create()
    } finally {
      Isolate.selfIsolate.set(null)
    }
  }

  def isolate[@spec(Int, Long, Double) T: Arrayable](proto: Proto[Isolate[T]], name: String = null): Channel[T] = {
    val scheduler = proto.scheduler match {
      case null => bundle.defaultScheduler
      case name => bundle.retrieve(name)
    }
    val (frame, channel) = monitor.synchronized {
      val eventQueue = new EventQueue.SingleSubscriberSyncedUnrolledRing[T](new util.Monitor)
      val uname = if (name == null) uniqueName() else ensureUnique(name)
      val frame = new IsolateFrame[T](
        uname,
        DefaultIsolateSystem.this,
        eventQueue,
        new Reactive.Emitter[SysEvent],
        new Reactive.Emitter[T],
        new Reactive.Emitter[Throwable],
        scheduler,
        new IsolateFrame.State,
        new AtomicReference(IsolateFrame.Created)
      )
      val isolate = Isolate.argFrame.withValue(frame) {
        createAndResetIsolate(proto)
      }
      frame.isolate = isolate
      val channel = new Channel.Synced(frame, new util.Monitor)
      frame.channel = channel
      isolates(uname) = frame
      (frame, channel)
    }
    scheduler.initiate(frame)
    frame.wake()
    channel
  }

}
