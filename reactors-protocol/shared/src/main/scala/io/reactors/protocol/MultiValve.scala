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
  private val oldest = RCell(0L)
  private val next = RCell(0L)
  private val needsFlush = (slowest zip oldest)(_ > _).changes.toSignal(false)
  private val flushing = needsFlush.is(true) on {
    val total = slowest() - oldest()
    while (needsFlush()) {
      oldest := oldest() + 1
    }
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
      flushing.chain(forwarding).andThen(c.seal())
    )
  }

  def +=(v: Valve[T]): Subscription = {
    val pos = RCell(math.min(slowest(), next()))
    valves += (v, pos)

    val morePending = (pos zip next)(_ < _).changes.toSignal(pos() < next())
    val available = (v.available zip morePending)(_ && _)
    val moving = available.is(true) on {
      while (available()) {
        val idx = (pos() - oldest()).toInt
        val x = ring(idx)
        v.channel ! x
        pos := pos() + 1
      }
    }

    moving.chain(available).chain(morePending).andThen(valves -= (v, pos))
  }
}
