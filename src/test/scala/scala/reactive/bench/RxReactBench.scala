package scala.reactive
package bench



import org.scalameter.api._
import rx.lang.scala._



class RxReactBench extends PerformanceTest.OfflineReport {

  val sumSizes = Gen.range("size")(100000, 500000, 100000)
  val stringArrays = for (sz <- sumSizes) yield {
    val ss = for (x <- 0 until sz) yield scala.util.Random.nextString(10)
    ss.toArray
  }

  performance of "sum" config(
    exec.minWarmupRuns -> 50,
    exec.maxWarmupRuns -> 100,
    exec.benchRuns -> 30,
    exec.independentSamples -> 1
  ) in {
    using(sumSizes) curve("Reactive Collections") in { sz =>
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
      val xs = subjects.BehaviorSubject[Int](0)
      val sum = xs.reduce(_ + _)

      var i = 0
      while (i < sz) {
        xs.onNext(i)
        i += 1
      }
    }

    using(sumSizes) curve("Rx-optimized") in { sz =>
      val xs = Subject[Int]()
      val sum = Observable[Int](subscriber => {
        var z = 0
        subscriber.add(xs.subscribe(
          x => { z += x },
          e => subscriber.onError(e),
          () => { subscriber.onNext(z); subscriber.onCompleted() }
        ))
      })

      var i = 0
      while (i < sz) {
        xs.onNext(i)
        i += 1
      }
    }

  }

}


