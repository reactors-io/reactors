package org.reactors
package benchmarks



import org.scalameter.api._
import org.scalameter.picklers.noPickler._



class EventBoxingBench extends Bench.Forked[Long] {
  override def defaultConfig: Context = Context(
    exec.independentSamples -> 1,
    verbose -> false
  )

  def measurer: Measurer[Long] =
    for (table <- Measurer.BoxingCount.all()) yield {
      table.copy(value = table.value.valuesIterator.sum)
    }

  def aggregator: Aggregator[Long] = Aggregator.median

  override def reporter = Reporter.Composite(
    LoggingReporter(),
    ValidationReporter()
  )

  measure method "Emitter.onX" config (
    reports.validation.predicate -> { (n: Any) => n == 0 }
  ) in {
    using(Gen.single("numEvents")(10000)) in { numEvents =>
      var sum = 0
      val emitter = new Events.Emitter[Int]
      emitter.onEvent(sum += _)
      emitter.on(sum += 1)

      var i = 0
      while (i < numEvents) {
        emitter.react(i)
        i += 1
      }
    }
  }

  measure method "Emitter.toSignal" config (
    reports.validation.predicate -> { (n: Any) => n == 4 }
  ) in {
    using(Gen.single("numEvents")(10000)) in { numEvents =>
      val emitter = new Events.Emitter[Int]
      val s0 = emitter.toSignal
      val s1 = emitter.toSignalWith(-1)

      var i = 0
      while (i < numEvents) {
        assert(s1() == i - 1)
        emitter.react(i)
        assert(s0() == i)
        i += 1
      }
    }
  }

  measure method "Emitter.scanPast+onX" config (
    reports.validation.predicate -> { (n: Any) => n == 3 }
  ) in {
    using(Gen.single("numEvents")(10000)) in { numEvents =>
      var count = 0
      val emitter = new Events.Emitter[Int]
      emitter.scanPast(0)(_ + _).onEvent(x => count += 1)
      emitter.scanPast(0)(_ + _).on(count += 1)
      emitter.scanPast(0)(_ + _).onDone({})

      var i = 0
      while (i < numEvents) {
        emitter.react(i)
        i += 1
      }
      emitter.unreact()
    }
  }

  measure method "Emitter.<combinators>" config (
    reports.validation.predicate -> { (n: Any) => n == 28 }
  ) in {
    using(Gen.single("numEvents")(10000)) in { numEvents =>
      val emitter = new Events.Emitter[Int]

      // count
      val count = emitter.count.toSignal

      // mutate
      object Cell {
        var x = 0
      }
      val cell = new Events.Mutable(Cell)
      val mutate = emitter.mutate(cell) { c => v =>
        c.x = v
      }
      val mutate2 = emitter.mutate(cell, cell) { (c1, c2) => v =>
        c2.x = c1.x
        c1.x = v
      }
      val mutate3 = emitter.mutate(cell, cell, cell) { (c1, c2, c3) => v =>
        c3.x = c2.x
        c2.x = c1.x
        c1.x = v
      }

      // after
      var a0 = 0
      val start = new Events.Emitter[Int]
      val after = emitter.after(start)
      after.on(a0 += 1)
      start.react(7)

      // until
      var u0 = 0
      val end = new Events.Emitter[Int]
      val until = emitter.until(end)
      until.on(u0 += 1)
      emitter.onEvent(x => if (x == 1000) end.react(x))

      // once
      var onceCount = 0
      val once = emitter.once
      once.on(onceCount += 1)
      once.onDone(onceCount += 1)

      // filter
      var filterCount = 0
      val filter = emitter.filter(_ % 2 == 1)
      filter.on(filterCount += 1)

      var i = 0
      while (i < numEvents) {
        assert(filterCount == i / 2)
        emitter.react(i)
        assert(count() == i + 1)
        assert(Cell.x == i)
        assert(onceCount == 2)
        i += 1
      }
      emitter.unreact()
    }
  }

}
