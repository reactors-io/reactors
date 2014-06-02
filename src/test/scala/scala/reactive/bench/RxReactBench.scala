package scala.reactive
package bench



import org.scalameter.api._
import rx.lang.scala._



class RxReactBench extends PerformanceTest.Regression {

  def persistor = Persistor.None

  val sumSizes = Gen.range("size")(100000, 500000, 100000)
  val aggregateSizes = Gen.range("size")(10, 100, 10)
  val setSizes = Gen.range("size")(200, 4000, 500)

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

    using(sumSizes) curve("Rx") in { sz =>
      val s = subjects.PublishSubject[Int](0)
      val sum = s.reduce(_ + _)

      var i = 0
      while (i < sz) {
        s.onNext(i)
        i += 1
      }
    }

  }

}


