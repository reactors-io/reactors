package io.reactors



import scala.collection._



class Remote(val system: ReactorSystem) extends Protocol.Service {
  private val transports = mutable.Map[String, Remote.Transport]()

  def resolve[@spec(Int, Long, Double) T: Arrayable](url: ChannelUrl): Channel[T] = {
    transports(url.reactorUrl.systemUrl.schema).newChannel[T](url)
  }

  def shutdown() {
    for ((schema, transport) <- transports) transport.shutdown()
  }
}


object Remote {
  trait Transport {
    def newChannel[@spec(Int, Long, Double) T: Arrayable](url: ChannelUrl): Channel[T]
    def shutdown(): Unit
  }
}
