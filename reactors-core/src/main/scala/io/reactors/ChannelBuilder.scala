package io.reactors



import scala.reflect.ClassTag
import io.reactors.common.Reflect



/** Used for building channel objects.
 */
class ChannelBuilder(
  val channelName: String,
  val isDaemon: Boolean,
  val eventQueueFactory: EventQueue.Factory,
  val shortcutLocal: Boolean
) {
  /** Associates a new name for the channel.
   */
  def named(name: String) =
    new ChannelBuilder(name, isDaemon, eventQueueFactory, shortcutLocal)

  /** Designates whether this channel can bypass the event queue for local sends.
   *
   *  This is `false` by default.
   */
  def shortcut =
    new ChannelBuilder(channelName, isDaemon, eventQueueFactory, true)

  /** Specifies a daemon channel.
   */
  def daemon = new ChannelBuilder(channelName, true, eventQueueFactory, shortcutLocal)

  /** Associates a new event queue factory.
   */
  def eventQueue(factory: EventQueue.Factory) =
    new ChannelBuilder(channelName, isDaemon, factory, shortcutLocal)

  /** Opens a new channel for this reactor.
   *
   *  @tparam Q        type of the events in the new channel
   *  @return          the connector object of the new channel
   */
  final def open[@spec(Int, Long, Double) Q: Arrayable]: Connector[Q] =
    Reactor.self.frame.openConnector[Q](
      channelName, eventQueueFactory, isDaemon, shortcutLocal)
}
