package io.reactors.protocol.instrument



import io.reactors._



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
  def newChannel[@spec(Int, Long, Double) T: Arrayable](url: ChannelUrl): Channel[T] = {
    new ScriptedTransport.ScriptedChannel(url)
  }

  override def shutdown(): Unit = {
  }
}


object ScriptedTransport {
  private class ScriptedChannel[@spec(Int, Long, Double) T](url: ChannelUrl)
  extends Channel[T] {
    def !(x: T): Unit = ???
  }
}
