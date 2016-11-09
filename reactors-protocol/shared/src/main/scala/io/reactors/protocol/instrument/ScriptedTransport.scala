package io.reactors.protocol.instrument



import io.reactors._



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
