package io.reactors
package protocol






/** Communication patterns based on backpressure.
 */
trait BackpressureProtocols {
  self: ServerProtocols =>

  object Backpressure {
    type Server[T] = self.Server[Channel[Long], Channel[T]]
    type Req[T] = self.Server.Req[Channel[Long], Channel[T]]

    class Link[T](private[reactors] val ch: Channel[T]) {
    }
  }

  implicit class BackpressureSystemOps(val system: ReactorSystem) {
    def backpressure[T]: Backpressure.Server[T] = ???
  }

  implicit class BackpressureChannelBuilderOps(val builder: ChannelBuilder) {
    def backpressure[T]: Connector[Backpressure.Req[T]] = ???
  }
}
