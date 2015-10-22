package scala.reactive



import scala.collection._
import scala.reactive.isolate.Frame



class Connector[@spec(Int, Long, Double) T](
  private[reactive] val sharedChannel: Channel.Shared[T],
  private[reactive] val queue: EventQueue[T],
  private[reactive] val eventsEmitter: Events.Emitter[T],
  private[reactive] val frame: Frame,
  val isDaemon: Boolean
) extends Identifiable {
  def uid = sharedChannel.asLocal.uid

  def channel: Channel[T] = sharedChannel

  def events: Events[T] = eventsEmitter

  def seal(): Unit = frame.sealConnector(sharedChannel.asLocal.uid)

  def dequeue(): Int = queue.dequeue(eventsEmitter)
}
