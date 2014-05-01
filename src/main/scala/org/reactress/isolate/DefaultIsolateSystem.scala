package org.reactress
package isolate



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

  def isolate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q: Arrayable](proto: Proto[ReactIsolate[T, Q]], name: String = null)(implicit scheduler: Scheduler): Channel[T] = {
    val (frame, channel) = monitor.synchronized {
      val eventQueue = new EventQueue.SingleSubscriberSyncedUnrolledRing[Q](new util.Monitor)
      val uname = if (name == null) uniqueName() else ensureUnique(name)
      val frame = new IsolateFrame[T, Q](
        uname,
        eventQueue,
        new Reactive.Emitter[Q],
        new Reactive.Emitter[Throwable],
        scheduler,
        new IsolateFrame.State
      )
      val isolate = ReactIsolate.argFrame.withValue(frame) {
        proto.create()
      }
      frame.isolate = isolate
      val channel = new Channel.Synced(frame, new util.Monitor)
      frame.channel = channel
      isolates(uname) = frame
      (frame, channel)
    }
    scheduler.initiate(frame)
    channel
  }

}
