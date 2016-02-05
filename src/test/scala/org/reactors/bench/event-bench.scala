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

  measure method "Emitter.scanPast" config (
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

}
