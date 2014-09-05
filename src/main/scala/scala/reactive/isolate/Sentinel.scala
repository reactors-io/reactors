package scala.reactive
package isolate



import scala.reactive.container.ReactMap



class Sentinel extends Iso[Sentinel.Req] {
  import Sentinel._

  private val channelMap = ReactMap[String, Register]

  react <<= events onCase {
    case reg @ Register(uid, name, channel) =>
      channelMap.applyOrNil(name) match {
        case null => channelMap(name) = reg
        case oreg => if (oreg.uid < reg.uid) channelMap(name) = reg
      }
    case ReqChannelNow(name, respChannel) =>
      val reg = channelMap.applyOrNil(name)
      if (reg == null) respChannel << RespChannel(name, -1, null)
      else respChannel << RespChannel(name, reg.uid, reg.channel)
    case ReqUnsealedChannel(name, respChannel) =>
      def isUnsealed(reg: Register) = reg != null && !reg.channel.isSealed
      val reg = channelMap.applyOrNil(name)
      if (isUnsealed(reg)) {
        respChannel << RespChannel(name, reg.uid, reg.channel)
      } else {
        var sub: Reactive.Subscription = null
        sub = channelMap.react(name).onEvent { reg =>
          if (isUnsealed(reg)) {
            respChannel << RespChannel(name, reg.uid, reg.channel)
            sub.unsubscribe()
          }
        }
      }
  }

}


object Sentinel {
  sealed trait Req
  case class Register(uid: Long, name: String, channel: Channel[_]) extends Req
  case class ReqChannelNow(name: String, responseChannel: Channel[Resp]) extends Req
  case class ReqUnsealedChannel(name: String, responseChannel: Channel[Resp]) extends Req

  sealed trait Resp
  case class RespChannel(name: String, uid: Long, channel: Channel[_]) extends Resp
}
