package io.reactors



import java.net.InetSocketAddress



package object remoting {
  case class SystemUrl(schema: String, host: String, port: Int) {
    lazy val inetSocketAddress = new InetSocketAddress(host, port)
  }
  
  case class ReactorUrl(systemUrl: SystemUrl, name: String)
  
  case class ChannelUrl(reactorUrl: ReactorUrl, anchor: String) {
    val channelName = s"${reactorUrl.name}#$anchor"
  }
}
