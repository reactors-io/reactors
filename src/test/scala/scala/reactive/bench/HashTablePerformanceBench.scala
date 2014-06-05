package scala.reactive
package bench



import scala.collection._
import org.scalameter.api._



class HashTablePerformanceBench extends PerformanceTest.Regression {

  def persistor = Persistor.None

  val hashTableSizes = Gen.range("size")(10000, 50000, 10000)
  val reactTables = for {
    sz <- hashTableSizes
  } yield {
    val m = ReactTable[Int, Int]
    for (i <- 1 until sz) m(i) = 0
    m
  }
  val reactSets = for {
    sz <- hashTableSizes
  } yield {
    val m = ReactHashSet[Int]
    for (i <- 1 until sz) m += i
    m
  }
  val hashSets = for {
    sz <- hashTableSizes
  } yield {
    val m = mutable.HashSet[Int]()
    for (i <- 1 until sz) m += i
    m
  }

  performance of "insert" config(
    exec.minWarmupRuns -> 30,
    exec.maxWarmupRuns -> 60,
    exec.benchRuns -> 36,
    exec.independentSamples -> 3,
    exec.outliers.suspectPercent -> 60,
    exec.reinstantiation.frequency -> 6
  ) in {

    measure method "ReactHashMap" in {
      using(hashTableSizes) curve("ReactHashMap") in { sz =>
        val m = new ReactHashMap[Int, String]
        for (i <- 1 until sz) m(i) = "value"
        m
      }
    }

    measure method "ReactTable" in {
      using(hashTableSizes) curve("ReactTable") in { sz =>
        val m = ReactTable[Int, Int]
        for (i <- 1 until sz) m(i) = i
        m
      }
    }

    measure method "ReactTable-stable" in {
      using(reactTables) curve("ReactTable") in { m =>
        val sz = m.size
        for (i <- 1 until sz) m(i) = i
      }
    }

    measure method "ReactHashSet" in {
      using(hashTableSizes) curve("ReactHashSet") in { sz =>
        val m = ReactHashSet[Int]
        for (i <- 1 until sz) m += i
        m
      }
    }

    measure method "ReactHashSet-stable" in {
      using(reactSets) curve("ReactHashSet") in { m =>
        val sz = m.size
        for (i <- 1 until sz) m += i
      }
    }

    measure method "mutable.HashMap" in {
      using(hashTableSizes) curve("mutable.HashMap") in { sz =>
        val m = mutable.Map[Int, String]()
        for (i <- 1 until sz) m(i) = "value"
        m
      }
    }

    measure method "mutable.HashSet" in {
      using(hashTableSizes) curve("mutable.HashSet") in { sz =>
        val m = mutable.Set[Int]()
        for (i <- 1 until sz) m += i
        m
      }
    }

    measure method "mutable.HashSet-stable" in {
      using(hashSets) curve("mutable.HashSet") in { m =>
        val sz = m.size
        for (i <- 1 until sz) m += i
      }
    }
  }

}

