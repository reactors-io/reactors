package io.reactors.protocol



import io.reactors.Arrayable
import io.reactors.RCell
import io.reactors.Reactor
import io.reactors.Subscription
import io.reactors.container.RHashSet
import io.reactors.container.RRing



class MultiValve[T: Arrayable](val window: Int) {
  private val ring = new RRing[T](window)
  private val valves = new RHashSet[(Valve[T], RCell[Long])]
  private val slowest = valves.map(_._2).toSignalAggregate(Long.MaxValue)(math.min)
  private var oldest = 0L
  private val next = RCell(0L)
  private val flush = Reactor.self.system.channels.daemon.open[Unit]
  flush.events on {
    val total = slowest() - oldest
    oldest += total
    ring.dequeueMany(total.toInt)
  }

  val out: Valve[T] = {
    val c = Reactor.self.system.channels.daemon.shortcut.open[T]
    val forwarding = c.events onEvent { x =>
      if (ring.available()) {
        ring.enqueue(x)
        next := next() + 1
        if (slowest() > next()) ring.dequeue()
      } else throw new IllegalStateException("Valve is not available.")
    }
    Valve(
      c.channel,
      ring.available,
      forwarding.andThen(c.seal()).andThen(valves.clear()).andThen(flush.seal())
    )
  }

  def +=(v: Valve[T]): Subscription = {
    val pos = RCell(math.min(slowest(), next()))
    valves += (v, pos)

    val morePending = (pos zip next)(_ < _).changes.toSignal(pos() < next())
    val available = (v.available zip morePending)(_ && _)
    val moving = available.is(true) on {
      while (available()) {
        val idx = (pos() - oldest).toInt
        val x = ring(idx)
        v.channel ! x
        pos := pos() + 1
      }
      val total = slowest() - oldest
      if (total > 0) {
        flush.channel ! ()
      }
    }

    moving.chain(available).chain(morePending).andThen(valves -= (v, pos))
  }
}
