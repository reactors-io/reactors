package io.reactors
package protocol






/** Scatter-gather communication patterns.
 */
trait ScatterGatherProtocols {
  /** Type of the scatter-gather channel.
   */
  type ScatterGather[T, S] = Server[Seq[T], Seq[S]]

  object ScatterGather {
    /** Type of the requests sent to the scatter-gather channel.
     */
    type Req[T, S] = Server.Req[Seq[T], Seq[S]]
  }

  implicit class ScatterGatherConnectorOps[T, S: Arrayable](
    val c: Connector[ScatterGather.Req[T, S]]
  ) {
    /** Given a scatter-gather connector, installs the scatter-gather protocol.
     *
     *  @param p       the routing policy for scattering the requests
     */
    def scatterGather(
      p: Router.Policy[Server.Req[T, S]]
    ): Server.State[Seq[T], Seq[S]] = {
      val scatter = Reactor.self.system.channels.open[Server.Req[T, S]]
      scatter.route(p)
      val Server.State(ch, sub) = c.asyncServe {
        xs => Events.sync(xs.map(x => scatter.channel ? x): _*).once
      }
      Server.State(ch, sub.andThen(scatter.seal()))
    }
  }
}
