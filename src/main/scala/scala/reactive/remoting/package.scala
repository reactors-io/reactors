package scala.reactive






package object remoting {
  case class SystemUrl(schema: String, host: String, port: Int)
  
  case class IsoUrl(systemUrl: SystemUrl, name: String)
  
  case class ChannelUrl(isoUrl: IsoUrl, anchor: String) {
    val channelName = s"${isoUrl.name}#$anchor"
  }
}
