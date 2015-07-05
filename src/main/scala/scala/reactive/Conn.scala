package scala.reactive



import scala.collection._
import scala.reactive.isolate.Frame



class Conn[@spec(Int, Long, Double) T](
  private val localChannel: Chan.Local[T],
  private val queue: EventQ[T],
  private val eventsEmitter: Events.Emitter[T],
  private val frame: Frame,
  val isDaemon: Boolean
) extends Identifiable {

  def uid = localChannel.uid

  def channel: Chan[T] = localChannel

  def events: Events[T] = eventsEmitter

  def seal(): Unit = frame.sealConnector(localChannel.uid)

}
