package io.reactors



import io.reactors.concurrent.Frame
import scala.collection._
import scala.reflect.ClassTag



/** A pair of a channel and its event stream.
 *
 *  Allows closing the channel with its `seal` operation.
 */
class Connector[@spec(Int, Long, Double) T](
  private[reactors] val sharedChannel: Channel.Shared[T],
  private[reactors] val queue: EventQueue[T],
  private[reactors] val frame: Frame,
  private[reactors] val extras: immutable.Map[Class[_], Any],
  val isDaemon: Boolean
) extends Identifiable {
  /** Returns the unique identifier of the channel.
   */
  def uid = sharedChannel.asLocal.uid

  /** Returns the channel.
   */
  def channel: Channel[T] = sharedChannel

  /** Returns the event stream.
   */
  def events: Events[T] = queue.events

  /** Seals the channel, preventing it from delivering additional events.
   */
  def seal(): Boolean = frame.sealConnector(sharedChannel.asLocal.uid)

  /** Returns extra information associated with this channel.
   */
  def extra[C: ClassTag]: C =
    extras(implicitly[ClassTag[C]].runtimeClass).asInstanceOf[C]

  private[reactors] def dequeue(): Int = queue.dequeue()
}
