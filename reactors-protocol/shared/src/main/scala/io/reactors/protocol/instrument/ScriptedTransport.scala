package io.reactors.protocol.instrument



import io.reactors._
import scala.collection._



/** Transport whose reliability can be scripted.
 *
 *  When used, the corresponding reactor system must have its configuration property
 *  `system.channels.create-as-local` set to `"false"`. In addition, this transport
 *  must be present in the `remote` section of configuration.
 *
 *  The main use of this class is for testing - it allows simulating unreliability on
 *  the network level, as well as specific failure scenarios.
 */
class ScriptedTransport(val system: ReactorSystem) extends Remote.Transport {
  private[instrument] case class Behavior(
    emitter: Events.Emitter[Any], subscription: Subscription
  )

  private[instrument] val channelBehaviors =
    mutable.Map[ScriptedTransport.ScriptedChannel[Any], Behavior]()

  private[instrument] def withChannel[T](
    ch: Channel[T], behavior: Events[T] => Events[T]
  ): Unit = {
    val sharedChannel = ch.asInstanceOf[Channel.Shared[T]]
    val scriptedChannel =
      sharedChannel.underlying.asInstanceOf[ScriptedTransport.ScriptedChannel[Any]]
    val emits = new Events.Emitter[T]
    val deliveries = behavior(emits)
    val isoName = sharedChannel.url.reactorUrl.name
    val channelName = sharedChannel.url.anchor
    val localChannel = system.channels.getLocal[T](isoName, channelName).get
    val subscription = deliveries.onEventOrDone {
      x => localChannel ! x
    } {
      channelBehaviors.remove(scriptedChannel)
    }

    channelBehaviors(scriptedChannel) =
      Behavior(emits.asInstanceOf[Events.Emitter[Any]], subscription)
  }

  def schema = "scripted"

  def newChannel[@spec(Int, Long, Double) T: Arrayable](url: ChannelUrl): Channel[T] = {
    new ScriptedTransport.ScriptedChannel(this, url)
  }

  override def shutdown(): Unit = {
    channelBehaviors.clear()
  }
}


object ScriptedTransport {
  private[instrument] class ScriptedChannel[@spec(Int, Long, Double) T](
    val transport: ScriptedTransport, val url: ChannelUrl
  ) extends Channel[T] {
    def !(x: T): Unit = {
      transport.channelBehaviors.get(this.asInstanceOf[ScriptedChannel[Any]]) match {
        case Some(behavior) =>
          behavior.emitter.asInstanceOf[Events.Emitter[T]].react(x)
        case None =>
          val isoName = url.reactorUrl.name
          val chName = url.anchor
          transport.system.channels.getLocal[T](isoName, chName) match {
            case Some(ch) => ch ! x
            case None =>
          }
      }
    }
  }
}
