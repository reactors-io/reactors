package io.reactors
package protocol






/** Communication patterns based on backpressure.
 */
trait BackpressureProtocols {
  self: ServerProtocols =>

  object Backpressure {
    type Server[T] = self.Server[Channel[Long], Link[T]]
    type Req[T] = self.Server.Req[Channel[Long], Link[T]]

    class Link[T](private val channel: Channel[T]) extends Serializable {
      def available: Signal[Boolean] = ???
      def trySend(x: T): Boolean = ???
    }
  }

  implicit class BackpressureSystemOps(val system: ReactorSystem) {
    def backpressure[T]: Backpressure.Server[T] = ???
  }

  implicit class BackpressureChannelBuilderOps(val builder: ChannelBuilder) {
    def backpressure[T]: Connector[Backpressure.Req[T]] = ???
  }
}
