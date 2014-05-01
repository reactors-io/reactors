package org.reactress
package isolate



import java.util.concurrent.atomic.AtomicReference
import scala.collection._



/** Default isolate system implementation.
 *
 *  @param name      the name of this isolate system
 */
class DefaultIsolateSystem(val name: String) extends IsolateSystem {
  private val isolates = mutable.Map[String, IsolateFrame[_, _]]()
  private var uniqueNameCount = 0L
  private val monitor = new util.Monitor

  protected def uniqueName() = {
    uniqueNameCount += 1
    s"isolate-$uniqueNameCount"
  } 

  def ensureUnique(name: String): String = {
    if (isolates contains name) error.illegalArg("isolate name '$name' already exists.")
    else name
  }

  private def createAndResetIsolate[T, Q](proto: Proto[ReactIsolate[T, Q]]): ReactIsolate[T, Q] = {
    val oldi = ReactIsolate.selfIsolate.get
    try {
      proto.create()
    } finally {
      ReactIsolate.selfIsolate.set(null)
    }
  }

  def isolate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q: Arrayable](proto: Proto[ReactIsolate[T, Q]], name: String = null)(implicit scheduler: Scheduler): Channel[T] = {
    val (frame, channel) = monitor.synchronized {
      val eventQueue = new EventQueue.SingleSubscriberSyncedUnrolledRing[Q](new util.Monitor)
      val uname = if (name == null) uniqueName() else ensureUnique(name)
      val frame = new IsolateFrame[T, Q](
        uname,
        eventQueue,
        new Reactive.Emitter[SysEvent],
        new Reactive.Emitter[Q],
        new Reactive.Emitter[Throwable],
        scheduler,
        new IsolateFrame.State,
        new AtomicReference(IsolateFrame.Created)
      )
      val isolate = ReactIsolate.argFrame.withValue(frame) {
        createAndResetIsolate(proto)
      }
      frame.isolate = isolate
      val channel = new Channel.Synced(frame, new util.Monitor)
      frame.channel = channel
      isolates(uname) = frame
      (frame, channel)
    }
    frame.wake()
    scheduler.initiate(frame)
    channel
  }

}
