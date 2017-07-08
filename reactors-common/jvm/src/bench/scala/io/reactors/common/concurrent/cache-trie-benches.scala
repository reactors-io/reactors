package io.reactors.common.concurrent



import java.util.concurrent.ConcurrentHashMap
import io.reactors.common.concurrent.CacheTrie.LNode
import io.reactors.common.concurrent.CacheTrie.SNode
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
    exec.minWarmupRuns -> 60,
    exec.maxWarmupRuns -> 120,
    exec.independentSamples -> 1,
    verbose -> true
  )

  case class Wrapper(value: Int)

  val elems = (0 until 1000000).map(i => Wrapper(i)).toArray

  val sizes = Gen.range("size")(100000, 1000000, 250000)

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
    (size, trie)
  }

  val artificialCachetries = for (size <- sizes) yield {
    val trie = new CacheTrie[Wrapper, Wrapper]
    trie.debugCachePopulate(16, elems(0), elems(0))
    (size, trie)
  }

//  @gen("chms")
//  @benchmark("cache-trie.apply")
//  @curve("CHM")
//  def chmLookup(sc: (Int, ConcurrentHashMap[Wrapper, Wrapper])): Int = {
//    val (size, chm) = sc
//    var i = 0
//    var sum = 0
//    while (i < size) {
//      sum += chm.get(elems(i)).value
//      i += 1
//    }
//    sum
//  }
//
//  @gen("cachetries")
//  @benchmark("cache-trie.apply")
//  @curve("cachetrie-slow-path")
//  def cachetrieSlowLookup(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
//    val (size, trie) = sc
//    var i = 0
//    var sum = 0
//    while (i < size) {
//      sum += trie.slowLookup(elems(i)).value
//      i += 1
//    }
//    sum
//  }
//
//  @gen("ctries")
//  @benchmark("cache-trie.apply")
//  @curve("ctrie")
//  def ctrie(sc: (Int, TrieMap[Wrapper, Wrapper])): Int = {
//    val (size, trie) = sc
//    var i = 0
//    var sum = 0
//    while (i < size) {
//      sum += trie.lookup(elems(i)).value
//      i += 1
//    }
//    sum
//  }

//  @gen("artificialCachetries")
//  @benchmark("cache-trie.apply")
//  @curve("cachetrie-fast-path")
//  def cachetrieFastLookup(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
//    val (size, trie) = sc
//    var i = 0
//    var sum = 0
//    io.reactors.test.delayTest(this.getClass)
//    while (i < size) {
//      val x = trie.fastLookup(elems(i))
//      sum += (if (x != null) x.value else 0)
//      i += 1
//    }
//    sum
//  }

//  @gen("cachetries")
//  @benchmark("cache-trie.apply")
//  @curve("cachetrie")
//  def cachetrieLookup(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
//    val (size, trie) = sc
//    var i = 0
//    var sum = 0
//    while (i < size) {
//      sum += trie.lookup(elems(i)).value
//      i += 1
//    }
//    sum
//  }
//
  @gen("sizes")
  @benchmark("cache-trie.insert")
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
//
//  @gen("sizes")
//  @benchmark("cache-trie.insert")
//  @curve("ctrie")
//  def ctrieInsert(size: Int) = {
//    val trie = new TrieMap[Wrapper, Wrapper]
//    var i = 0
//    while (i < size) {
//      val v = elems(i)
//      trie.put(v, v)
//      i += 1
//    }
//    trie
//  }
//
  @gen("sizes")
  @benchmark("cache-trie.insert")
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

  def perLevelDistribution(sz: Int): Unit = {
    val trie = new CacheTrie[String, String]
    var i = 0
    while (i < sz) {
      trie.insert(i.toString + "/" + sz.toString, i.toString)
      i += 1
    }
    val histogram = new Array[Int](10)
    def traverse(node: Array[AnyRef], level: Int): Unit = {
      var i = 0
      while (i < node.length) {
        val old = node(i)
        if (old.isInstanceOf[SNode[_, _]]) {
          histogram(level / 4) += 1
        } else if (old.isInstanceOf[LNode[_, _]]) {
          var ln = old.asInstanceOf[LNode[_, _]]
          while (ln != null) {
            histogram(level / 4) += 1
            ln = ln.next
          }
        } else if (old.isInstanceOf[Array[AnyRef]]) {
          val an = old.asInstanceOf[Array[AnyRef]]
          traverse(an, level + 4)
        } else if (old eq null) {
        } else {
          sys.error(s"Unexpected case: $old")
        }
        i += 1
      }
    }
    traverse(trie.debugReadRoot, 0)
    println(s":: size $sz ::")
    for (i <- 0 until histogram.length) {
      println(f"${i * 4}%3d: ${histogram(i)}%8d ${"*" * (histogram(i) * 40 / sz)}")
    }
  }

  test("per level distribution") {
    perLevelDistribution(1000)
    perLevelDistribution(2000)
    perLevelDistribution(3000)
    perLevelDistribution(5000)
    perLevelDistribution(10000)
    perLevelDistribution(20000)
    perLevelDistribution(30000)
    perLevelDistribution(50000)
    perLevelDistribution(100000)
    perLevelDistribution(200000)
    perLevelDistribution(300000)
    perLevelDistribution(400000)
    perLevelDistribution(500000)
    perLevelDistribution(650000)
    perLevelDistribution(800000)
    perLevelDistribution(1000000)
    perLevelDistribution(1500000)
    perLevelDistribution(2000000)
  }
}
