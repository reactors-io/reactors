package io.reactors
package protocol



import io.reactors.common.UnrolledRing



/** Scatter-gather communication patterns.
 */
trait RendezvousProtocols {
  def rendezvous[T: Arrayable, S: Arrayable]: (Server[T, S], Server[S, T]) = {
    val builder = Reactor.self.system.channels.daemon
    val (ct, cs) = (builder.open[T], builder.open[S])
    val (qt, qs) = (new UnrolledRing[T], new UnrolledRing[S])
    def flush(): Unit = while (qt.nonEmpty && qs.nonEmpty) {
      ct.channel ! qt.dequeue()
      cs.channel ! qs.dequeue()
    }
    def meet[X, Y](x: X, qx: Queue[X], ey: Events[Y]): Events[Y] = {
      qx.enqueue(x)
      flush()
      ey
    }
    ???
  }
}
