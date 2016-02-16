package org.reactors



import org.reactors.concurrent.Frame
import scala.collection._



/** A pair of a channel and its event stream.
 *
 *  Allows closing the channel with its `seal` operation.
 */
class Connector[@spec(Int, Long, Double) T](
  private[reactors] val sharedChannel: Channel.Shared[T],
  private[reactors] val queue: EventQueue[T],
  private[reactors] val frame: Frame,
  val isDaemon: Boolean
) extends Identifiable {
  def uid = sharedChannel.asLocal.uid

  def channel: Channel[T] = sharedChannel

  def events: Events[T] = queue.events

  def seal(): Unit = frame.sealConnector(sharedChannel.asLocal.uid)

  def dequeue(): Int = queue.dequeue()
}
