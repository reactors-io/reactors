package scala.reactive
package bench



import scala.collection._
import org.scalameter.api._



class HashTableMemoryBench extends PerformanceTest.Regression {

  def persistor = Persistor.None
  override def measurer = new Executor.Measurer.MemoryFootprint

  val hashTableSizes = Gen.range("size")(10000, 50000, 10000)

  performance of "memory" config(
    exec.minWarmupRuns -> 10,
    exec.maxWarmupRuns -> 30,
    exec.benchRuns -> 30,
    exec.independentSamples -> 1
  ) in {
    using(hashTableSizes) curve("RHashMap") in { sz =>
      val m = RHashMap[Int, String]
      for (i <- 0 until sz) m(i) = "value"
      m
    }
    using(hashTableSizes) curve("RHashValMap") in { sz =>
      val m = RHashValMap[Int, Int]
      for (i <- 0 until sz) m(i) = i
      m
    }
    using(hashTableSizes) curve("RHashSet") in { sz =>
      val m = RHashSet[Int]
      for (i <- 0 until sz) m += i
      m
    }
    using(hashTableSizes) curve("mutable.HashMap") in { sz =>
      val m = mutable.Map[Int, String]()
      for (i <- 0 until sz) m(i) = "value"
      m
    }
    using(hashTableSizes) curve("mutable.HashSet") in { sz =>
      val m = mutable.Set[Int]()
      for (i <- 0 until sz) m += i
      m
    }
  }

}

