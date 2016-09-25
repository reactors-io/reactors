package io.reactors



import scala.collection._



/** Service that tracks different transports for remote communication.
 *
 *  The most important method is `resolve`, which creates a channel from a
 *  channel URL. This allows communication with reactors in non-local
 *  reactor systems, e.g. in another process, or on another machine.
 */
class Remote(val system: ReactorSystem) extends Protocol.Service {
  private val transports = mutable.Map[String, Remote.Transport]()

  for ((tp, t) <- system.bundle.urlMap) {
    val transportCtor =
      Class.forName(t.transportName).getConstructor(classOf[ReactorSystem])
    val transport = transportCtor.newInstance(system).asInstanceOf[Remote.Transport]
    transports(t.url.schema) = transport
  }

  def transport(schema: String) = transports(schema)

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
