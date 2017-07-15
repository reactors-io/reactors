package io.reactors.common.concurrent



import java.util.concurrent.ConcurrentHashMap
import org.scalameter.api._
import org.scalameter.japi.JBench
import org.scalatest.FunSuite
import scala.collection.concurrent.TrieMap
import scala.util.Random



class CacheTrieFootprintBenches extends JBench.OfflineReport {
  override def measurer = new Executor.Measurer.MemoryFootprint

  override def defaultConfig = Context(
    exec.benchRuns -> 8,
    exec.independentSamples -> 1,
    verbose -> true
  )

  val sizes = Gen.range("size")(100000, 1000000, 250000)

  case class Wrapper(value: Int)

  val elems = (0 until 1000000).map(i => Wrapper(i)).toArray

  @gen("sizes")
  @benchmark("cache-trie.size")
  @curve("chm")
  def chmInsert(size: Int) = {
    val chm = new ConcurrentHashMap[Wrapper, Wrapper]
    var i = 0
    while (i < size) {
      val v = elems(i)
      chm.put(v, v)
      i += 1
    }
    chm
  }

  @gen("sizes")
  @benchmark("cache-trie.size")
  @curve("ctrie")
  def ctrieInsert(size: Int) = {
    val trie = new TrieMap[Wrapper, Wrapper]
    var i = 0
    while (i < size) {
      val v = elems(i)
      trie.put(v, v)
      i += 1
    }
    trie
  }

  @gen("sizes")
  @benchmark("cache-trie.size")
  @curve("cachetrie")
  def cachetrieInsert(size: Int) = {
    val trie = new CacheTrie[Wrapper, Wrapper]
    var i = 0
    while (i < size) {
      val v = elems(i)
      trie.insert(v, v)
      i += 1
    }
    trie
  }
}


class CacheTrieBenches extends JBench.OfflineReport {
  override def historian =
    org.scalameter.reporting.RegressionReporter.Historian.Complete()

  override def defaultConfig = Context(
    exec.minWarmupRuns -> 40,
    exec.maxWarmupRuns -> 80,
    exec.independentSamples -> 1,
    exec.jvmflags -> List("-server", "-verbose:gc", "-Xmx3048m", "-Xms3048m"),
    verbose -> true
  )

  case class Wrapper(value: Int)

  @transient
  lazy val elems = (0 until 25000000).map(i => Wrapper(i)).toArray

  val sizes = Gen.range("size")(100000, 1000000, 100000)

  val chms = for (size <- sizes) yield {
    val chm = new ConcurrentHashMap[Wrapper, Wrapper]
    for (i <- 0 until size) chm.put(elems(i), elems(i))
    (size, chm)
  }

  val ctries = for (size <- sizes) yield {
    val trie = new TrieMap[Wrapper, Wrapper]
    for (i <- 0 until size) trie.put(elems(i), elems(i))
    (size, trie)
  }

  val cachetries = for (size <- sizes) yield {
    val trie = new CacheTrie[Wrapper, Wrapper]
    for (i <- 0 until size) {
      trie.insert(elems(i), elems(i))
    }
    for (i <- 0 until size) {
      trie.lookup(elems(i))
    }
    for (i <- 0 until size) {
      trie.lookup(elems(i))
    }
    (size, trie)
  }

  val artificialCachetries = for (size <- sizes) yield {
    val trie = new CacheTrie[Wrapper, Wrapper]
    //trie.debugCachePopulate(20, elems(0), elems(0))
    //trie.debugCachePopulateTwoLevel(20, elems, elems)
    trie.debugCachePopulateOneLevel(20, elems, elems)
    (size, trie)
  }

  //@gen("chms")
  //@benchmark("cache-trie.apply")
  //@curve("CHM")
  //def chmLookup(sc: (Int, ConcurrentHashMap[Wrapper, Wrapper])): Int = {
  //  val (size, chm) = sc
  //  var i = 0
  //  var sum = 0
  //  while (i < size) {
  //    sum += chm.get(elems(i)).value
  //    i += 1
  //  }
  //  sum
  //}
  //
  //@gen("cachetries")
  //@benchmark("cache-trie.apply")
  //@curve("cachetrie-slow-path")
  //def cachetrieSlowLookup(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
  //  val (size, trie) = sc
  //  var i = 0
  //  var sum = 0
  //  while (i < size) {
  //    sum += trie.slowLookup(elems(i)).value
  //    i += 1
  //  }
  //  sum
  //}

  //@gen("ctries")
  //@benchmark("cache-trie.apply")
  //@curve("ctrie")
  //def ctrie(sc: (Int, TrieMap[Wrapper, Wrapper])): Int = {
  //  val (size, trie) = sc
  //  var i = 0
  //  var sum = 0
  //  while (i < size) {
  //    sum += trie.lookup(elems(i)).value
  //    i += 1
  //  }
  //  sum
  //}
  //
  @gen("artificialCachetries")
  @benchmark("cache-trie.apply")
  @curve("cachetrie-fast-path")
  def cachetrieFastLookup(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
    val (size, trie) = sc
    var i = 0
    var sum = 0
    io.reactors.test.delayTest(this.getClass)
    while (i < size) {
      val x = trie.fastLookup(elems(i))
      sum += (if (x != null) x.value else 0)
      i += 1
    }
    sum
  }

  @gen("cachetries")
  @benchmark("cache-trie.apply")
  @curve("cachetrie")
  def cachetrieLookup(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
    val (size, trie) = sc
    var i = 0
    var sum = 0
    while (i < size) {
      sum += trie.lookup(elems(i)).value
      i += 1
    }
    //println(trie.debugPerLevelDistribution)
    //println(trie.debugCacheStats)
    sum
  }

  // @gen("sizes")
  // @benchmark("cache-trie.insert")
  // @curve("chm")
  // def chmInsert(size: Int) = {
  //   val chm = new ConcurrentHashMap[Wrapper, Wrapper]
  //   var i = 0
  //   while (i < size) {
  //     val v = elems(i)
  //     chm.put(v, v)
  //     i += 1
  //   }
  //   chm
  // }
  //
  //@gen("sizes")
  //@benchmark("cache-trie.insert")
  //@curve("ctrie")
  //def ctrieInsert(size: Int) = {
  //  val trie = new TrieMap[Wrapper, Wrapper]
  //  var i = 0
  //  while (i < size) {
  //    val v = elems(i)
  //    trie.put(v, v)
  //    i += 1
  //  }
  //  trie
  //}
  //
  //@gen("sizes")
  //@benchmark("cache-trie.insert")
  //@curve("cachetrie")
  //def cachetrieInsert(size: Int) = {
  //  val trie = new CacheTrie[Wrapper, Wrapper]
  //  var i = 0
  //  while (i < size) {
  //    val v = elems(i)
  //    trie.insert(v, v)
  //    i += 1
  //  }
  //  trie
  //}
}


class BirthdaySimulations extends FunSuite {
  test("run birthday simulations") {
    birthday(4, 1)
    birthday(16, 1)
    birthday(16, 2)
    birthday(32, 1)
    birthday(32, 2)
    birthday(256, 1)
    birthday(4096, 1)
    birthday(4096, 2)
    birthday(1 << 16, 1)
    birthday(1 << 20, 1)
  }

  def birthday(days: Int, collisions: Int): Unit = {
    var sum = 0L
    val total = 1000
    for (k <- 1 to total) {
      val slots = new Array[Int](days)
      var i = 1
      while (i <= days) {
        val day = Random.nextInt(days)
        if (slots(day) == collisions) {
          sum += i - 1
          i = days + 2
        }
        slots(day) += 1
        i += 1
      }
      if (i == days + 1) {
        sum += i
      }
    }
    println(s"For $days, collisions $collisions, average: ${(1.0 * sum / total)}")
  }

  def debugPerLevelDistribution(sz: Int): Unit = {
    val trie = new CacheTrie[String, String]
    var i = 0
    while (i < sz) {
      trie.insert(i.toString + "/" + sz.toString, i.toString)
      i += 1
    }
    for (i <- 0 until 512) trie.lookup(i.toString)
    println(trie.debugPerLevelDistribution)
  }

  test("per level distribution") {
    debugPerLevelDistribution(1000)
    debugPerLevelDistribution(2000)
    debugPerLevelDistribution(3000)
    debugPerLevelDistribution(5000)
    debugPerLevelDistribution(10000)
    debugPerLevelDistribution(20000)
    debugPerLevelDistribution(30000)
    debugPerLevelDistribution(50000)
    debugPerLevelDistribution(100000)
    debugPerLevelDistribution(200000)
    debugPerLevelDistribution(300000)
    debugPerLevelDistribution(400000)
    debugPerLevelDistribution(500000)
    debugPerLevelDistribution(650000)
    debugPerLevelDistribution(800000)
    debugPerLevelDistribution(1000000)
    debugPerLevelDistribution(1200000)
    debugPerLevelDistribution(1500000)
    debugPerLevelDistribution(1800000)
    debugPerLevelDistribution(2000000)
    debugPerLevelDistribution(2200000)
    debugPerLevelDistribution(2500000)
    debugPerLevelDistribution(3000000)
  }
}
