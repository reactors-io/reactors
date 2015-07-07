package scala.reactive



import scala.collection._
import scala.reactive.isolate.Frame



class Conn[@spec(Int, Long, Double) T](
  private[reactive] val localChannel: Chan.Local[T],
  private[reactive] val queue: EventQ[T],
  private[reactive] val eventsEmitter: Events.Emitter[T],
  private[reactive] val frame: Frame,
  val isDaemon: Boolean
) extends Identifiable {

  def uid = localChannel.uid

  def channel: Chan[T] = localChannel

  def events: Events[T] = eventsEmitter

  def seal(): Unit = frame.sealConnector(localChannel.uid)

  private[reactive] def releaseEvent(): Int = ???

}
