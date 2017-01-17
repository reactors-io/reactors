package io.reactors
package protocol



import io.reactors.common.UnrolledRing



/** Scatter-gather communication patterns.
 */
trait RendezvousProtocols {
  def rendezvous[T: Arrayable, S: Arrayable]: Rendezvous[T, S] = {
    val builder = Reactor.self.system.channels.daemon
    val (ct, cs) = (builder.open[T], builder.open[S])
    val (qt, qs) = (new UnrolledRing[T], new UnrolledRing[S])
    def flush(): Unit = while (qt.nonEmpty && qs.nonEmpty) {
      ct.channel ! qt.dequeue()
      cs.channel ! qs.dequeue()
    }
    def meet[X, Y](x: X, qx: UnrolledRing[X], ey: Events[Y]): Events[Y] = {
      qx.enqueue(x)
      flush()
      ey
    }
    val ts = builder.server[T, S].asyncServe(t => meet(t, qt, cs.events))
    val st = builder.server[S, T].asyncServe(s => meet(s, qs, ct.events))
    Rendezvous(ts.channel, st.channel, ts.subscription.chain(st.subscription))
  }
}


case class Rendezvous[T, S](
  left: Server[T, S],
  right: Server[S, T],
  subscription: Subscription
)
