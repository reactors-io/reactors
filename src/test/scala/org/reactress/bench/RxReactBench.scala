package org.reactress
package bench



import org.scalameter.api._
import rx.lang.scala._
import scala.react._



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
      val sum = e.foldPast(0)(_ + _)

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

    using(sumSizes) curve("ScalaReact") in { sz =>
      import RxReactBench.domain._
      val app = new ReactiveApp {
        override def main() {
          val source = EventSource[Int]
          val sum = source.scan(0)(_ + _).hold(0)

          var i = 0
          while (i < sz) {
            source << i
            i += 1
          }
          //println(sum.getValue) // this doesn't even produce the right value
        }
      }
      app.main(new Array(0))
    }
  }

  performance of "ReactCommuteAggregate" config(
    exec.minWarmupRuns -> 50,
    exec.maxWarmupRuns -> 100,
    exec.benchRuns -> 30,
    exec.independentSamples -> 1
  ) in {
    using(aggregateSizes) curve("Reactress-O(logn)") in { sz =>
      val catamorph = HigherCatamorph.monoid[Int](Commutoid(0)(_ + _))
      val signals = (for (i <- 0 until sz) yield new ReactCell(i)).toArray
      for (s <- signals) catamorph += s
      
      var i = 0
      while (i < 50000) {
        signals(i % sz) := i
        i += 1
      }
    }

    using(aggregateSizes) curve("Reactress-O(n)") in { sz =>
      val signals = for (i <- 0 until sz) yield new ReactCell(i)
      val staticAggregate = (signals.toList: List[Signal[Int]]).toArray.reduceLeft((zipped, signal) => (zipped zip signal)(_ + _))

      var i = 0
      while (i < 50000) {
        signals(i % sz) := i
        i += 1
      }
    }
  }

  performance of "ReactSet" config(
    exec.minWarmupRuns -> 50,
    exec.maxWarmupRuns -> 100,
    exec.benchRuns -> 30,
    exec.independentSamples -> 1
  ) in {
    using(setSizes) curve("Reactress-O(1)") in { sz =>
      val set = new ReactSet[Int]
      val keys = set.inserts
      val signals = for (i <- 0 until sz) yield new ReactCell(i)
      keys onValue {
        x => signals(x) := x
      }
      
      var i = 0
      while (i < 500000) {
        set.add(i % sz)
        i += 1
      }
    }

    using(setSizes) curve("Reactress-O(r)") in { sz =>
      val set = new ReactSet[Int]
      val keys = set.inserts
      val signals = for (i <- 0 until sz) yield keys.filter(_ == i)
      
      var i = 0
      while (i < 500000) {
        set.add(i % sz)
        i += 1
      }
    }
  }

}


object RxReactBench {

  import java.util.ArrayDeque
  
  object domain extends Domain {
    var engine = new TestEngine
    val scheduler = new ManualScheduler
  
    private val postTurnTodos = new ArrayDeque[() => Unit]
    def schedulePostTurn(op: => Unit) = postTurnTodos add (() => op)
  
    private def reset() {
      turnQueue.clear()
      postTurnTodos.clear()
      engine = new TestEngine
    }
  
    // add some test hooks to the standard engine
    class TestEngine extends Engine {
      override def runTurn() = super.runTurn()
      override def propagate() = {
        super.propagate()
        level = Int.MaxValue
        while (!postTurnTodos.isEmpty)
          postTurnTodos.poll().apply()
      }
      override def uncaughtException(e: Throwable) = {
        e.printStackTrace()
        postTurnTodos.clear()
        throw e
      }
    }
  
    private val turnQueue = new ArrayDeque[() => Unit]
  
    // First run the given op, and then a turn.
    def turn(op: => Unit) {
      turnQueue add (() => op)
    }
  
    // run at the very end of current turn
    def postTurn(op: => Unit) = domain.schedulePostTurn(op)
  
    override def toString = "BenchDomain"
  }

}