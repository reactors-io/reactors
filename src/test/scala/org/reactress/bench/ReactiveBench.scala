package org.reactress
package bench



import org.scalameter.api._



class ReactiveBench extends PerformanceTest.Regression {

  def persistor = Persistor.None

  val sumSizes = Gen.range("size")(100000, 500000, 100000)

  performance of "sum" config(
    exec.minWarmupRuns -> 50,
    exec.maxWarmupRuns -> 100,
    exec.benchRuns -> 30,
    exec.independentSamples -> 1
  ) in {
    using(sumSizes) curve("Reactress") in { sz =>
      val e = new Reactive.Emitter[Int]
      val sum = e.scanPast(0)(_ + _)

      var i = 0
      while (i < sz) {
        e += i
        i += 1
      }
      sum()
    }
  }

}

