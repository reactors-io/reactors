package io.reactors.common.concurrent



import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import org.scalameter.api._
import org.scalameter.execution.LocalExecutor
import org.scalameter.japi.JBench
import org.scalatest.FunSuite
import org.scalameter.picklers.Implicits._
import scala.collection.concurrent.TrieMap
import scala.util.Random



class CacheTrieRemoveBenches extends JBench.OfflineReport {
  override def historian =
    org.scalameter.reporting.RegressionReporter.Historian.Complete()

  override def reporter: Reporter[Double] = Reporter.Composite(
    new RegressionReporter(tester, historian),
    HtmlReporter(!online),
    new PGFPlotsReporter[Double](
      referenceCurve = "CHM",
      legend = false,
      cutoffs = Set("skiplist")
    )
  )

  override def defaultConfig = Context(
    exec.minWarmupRuns -> 80,
    exec.maxWarmupRuns -> 200,
    exec.independentSamples -> 1,
    exec.benchRuns -> 30,
    exec.jvmflags -> List("-server", "-verbose:gc", "-Xmx6092m", "-Xms6092m"),
    verbose -> true
  )

  case class Wrapper(value: Int) extends Comparable[Wrapper] {
    def compareTo(that: Wrapper) = this.value - that.value
  }

  val maxElems = 1000000

  @transient
  lazy val elems = Random.shuffle((0 until maxElems).toVector)
    .map(i => Wrapper(i)).toArray

  val sizes = Gen.enumeration("\\#keys")(100000)
  // val sizes = Gen.enumeration("\\#keys")(250000)
  // val sizes = Gen.enumeration("\\#keys")(500000)
  // val sizes = Gen.enumeration("\\#keys")(100000, 250000, 500000)

  val pars = Gen.enumeration("\\#proc")(1, 2, 3, 4, 5, 6, 7, 8)

  val parElems = 400000 

  val parCachetries = for (p <- pars) yield {
    val trie = new CacheTrie[Wrapper, Wrapper]
    for (i <- 0 until parElems) trie.insert(elems(i), elems(i))
    for (i <- 0 until parElems) trie.lookup(elems(i))
    for (i <- 0 until parElems) trie.lookup(elems(i))
    (p, trie)
  }

  val parCachetriesWithoutCompression = for (p <- pars) yield {
    val trie = new CacheTrie[Wrapper, Wrapper](
      doCompression = false, useCounters = false
    )
    for (i <- 0 until parElems) trie.insert(elems(i), elems(i))
    for (i <- 0 until parElems) trie.lookup(elems(i))
    for (i <- 0 until parElems) trie.lookup(elems(i))
    (p, trie)
  }

  val parCachetriesWithoutCounters = for (p <- pars) yield {
    val trie = new CacheTrie[Wrapper, Wrapper](
      doCompression = true, useCounters = false
    )
    for (i <- 0 until parElems) trie.insert(elems(i), elems(i))
    for (i <- 0 until parElems) trie.lookup(elems(i))
    for (i <- 0 until parElems) trie.lookup(elems(i))
    (p, trie)
  }

  val parChms = for (p <- pars) yield {
    val chm = new ConcurrentHashMap[Wrapper, Wrapper]
    for (i <- 0 until parElems) chm.put(elems(i), elems(i))
    (p, chm)
  }

  val parSkiplists = for (p <- pars) yield {
    val skiplist = new ConcurrentSkipListMap[Wrapper, Wrapper]
    for (i <- 0 until parElems) skiplist.put(elems(i), elems(i))
    (p, skiplist)
  }

  val parCtries = for (p <- pars) yield {
    val ctrie = new TrieMap[Wrapper, Wrapper]
    for (i <- 0 until parElems) ctrie.put(elems(i), elems(i))
    (p, ctrie)
  }

  val chms = for (size <- sizes) yield {
    val chm = new ConcurrentHashMap[Wrapper, Wrapper]
    for (i <- 0 until size) chm.put(elems(i), elems(i))
    (size, chm)
  }

  val skiplists = for (size <- sizes) yield {
    val skiplist = new ConcurrentSkipListMap[Wrapper, Wrapper]
    for (i <- 0 until size) skiplist.put(elems(i), elems(i))
    (size, skiplist)
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

  val cachetriesWithoutCounters = for (size <- sizes) yield {
    val trie = new CacheTrie[Wrapper, Wrapper](
      useCounters = false, doCompression = true
    )
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

  val cachetriesWithoutCompression = for (size <- sizes) yield {
    val trie = new CacheTrie[Wrapper, Wrapper](
      useCounters = false, doCompression = false
    )
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

  def chmRefill(sc: (Int, ConcurrentHashMap[Wrapper, Wrapper])): Unit = {
    val (size, chm) = sc
    var i = 0
    while (i < size) {
      chm.put(elems(i), elems(i))
      i += 1
    }
  }

  def skiplistRefill(sc: (Int, ConcurrentSkipListMap[Wrapper, Wrapper])): Unit = {
    val (size, skiplist) = sc
    var i = 0
    while (i < size) {
      skiplist.put(elems(i), elems(i))
      i += 1
    }
  }

  def ctrieRefill(sc: (Int, TrieMap[Wrapper, Wrapper])): Unit = {
    val (size, trie) = sc
    var i = 0
    while (i < size) {
      trie.put(elems(i), elems(i))
      i += 1
    }
  }

  def cachetrieRefill(sc: (Int, CacheTrie[Wrapper, Wrapper])): Unit = {
    val (size, trie) = sc
    var i = 0
    while (i < size) {
      trie.insert(elems(i), elems(i))
      i += 1
    }
    i = 0
    while (i < size) {
      trie.lookup(elems(i))
      i += 1
    }
    i = 0
    while (i < size) {
      trie.lookup(elems(i))
      i += 1
    }
  }

  def parChmRefill(sc: (Int, ConcurrentHashMap[Wrapper, Wrapper])): Unit = {
    val (p, chm) = sc
    var i = 0
    while (i < parElems) {
      chm.put(elems(i), elems(i))
      i += 1
    }
  }

  def parSkiplistRefill(sc: (Int, ConcurrentSkipListMap[Wrapper, Wrapper])): Unit = {
    val (p, skiplist) = sc
    var i = 0
    while (i < parElems) {
      skiplist.put(elems(i), elems(i))
      i += 1
    }
  }

  def parCtrieRefill(sc: (Int, TrieMap[Wrapper, Wrapper])): Unit = {
    val (p, trie) = sc
    var i = 0
    while (i < parElems) {
      trie.put(elems(i), elems(i))
      i += 1
    }
  }

  def parCachetrieRefill(sc: (Int, CacheTrie[Wrapper, Wrapper])): Unit = {
    val (p, trie) = sc
    var i = 0
    while (i < parElems) {
      trie.insert(elems(i), elems(i))
      i += 1
    }
    i = 0
    while (i < parElems) {
      trie.lookup(elems(i))
      i += 1
    }
    i = 0
    while (i < parElems) {
      trie.lookup(elems(i))
      i += 1
    }
  }

  // @gen("chms")
  // @benchmark("cache-trie.remove")
  // @setup("chmRefill")
  // @curve("CHM")
  // def chmRemove(sc: (Int, ConcurrentHashMap[Wrapper, Wrapper])): Int = {
  //   val (size, chm) = sc
  //   var i = 0
  //   var sum = 0
  //   while (i < size) {
  //     sum += chm.remove(elems(i)).value
  //     i += 1
  //   }
  //   sum
  // }

  // @gen("cachetriesWithoutCompression")
  // @benchmark("cache-trie.remove")
  // @setup("cachetrieRefill")
  // @curve("no-compact")
  // def cachetrieRemoveNoCompress(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
  //   val (size, trie) = sc
  //   var i = 0
  //   var sum = 0
  //   while (i < size) {
  //     sum += trie.remove(elems(i)).value
  //     i += 1
  //   }
  //   // println(trie.debugCacheStats)
  //   sum
  // }

  // @gen("cachetriesWithoutCounters")
  // @benchmark("cache-trie.remove")
  // @setup("cachetrieRefill")
  // @curve("no-counter")
  // def cachetrieRemoveNoCount(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
  //   val (size, trie) = sc
  //   var i = 0
  //   var sum = 0
  //   while (i < size) {
  //     sum += trie.remove(elems(i)).value
  //     i += 1
  //   }
  //   // println(trie.debugCacheStats)
  //   sum
  // }

  // @gen("cachetries")
  // @benchmark("cache-trie.remove")
  // @setup("cachetrieRefill")
  // @curve("cachetrie")
  // def cachetrieRemove(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
  //   val (size, trie) = sc
  //   var i = 0
  //   var sum = 0
  //   while (i < size) {
  //     sum += trie.remove(elems(i)).value
  //     i += 1
  //   }
  //   // println(trie.debugCacheStats)
  //   sum
  // }

  // @gen("ctries")
  // @benchmark("cache-trie.remove")
  // @setup("ctrieRefill")
  // @curve("ctrie")
  // def ctrieRemove(sc: (Int, TrieMap[Wrapper, Wrapper])): Int = {
  //   val (size, trie) = sc
  //   var i = 0
  //   var sum = 0
  //   while (i < size) {
  //     sum += trie.remove(elems(i)).get.value
  //     i += 1
  //   }
  //   sum
  // }

  // @gen("skiplists")
  // @benchmark("cache-trie.remove")
  // @setup("skiplistRefill")
  // @curve("skiplist")
  // def skiplistRemove(sc: (Int, ConcurrentSkipListMap[Wrapper, Wrapper])): Int = {
  //   val (size, skiplist) = sc
  //   var i = 0
  //   var sum = 0
  //   while (i < size) {
  //     sum += skiplist.remove(elems(i)).value
  //     i += 1
  //   }
  //   sum
  // }

  @gen("parChms")
  @benchmark("cache-trie.par.remove")
  @setup("parChmRefill")
  @curve("CHM")
  def chmParRemove(sc: (Int, ConcurrentHashMap[Wrapper, Wrapper])) = {
    val (p, chm) = sc
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var i = 0
        while (i < parElems / p) {
          val v = elems(k * parElems / p + i)
          chm.remove(v)
          i += 1
        }
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    chm
  }

  @gen("parCachetriesWithoutCompression")
  @benchmark("cache-trie.par.remove")
  @setup("parCachetrieRefill")
  @curve("no-compact")
  def cachetrieParRemoveNoCompress(sc: (Int, CacheTrie[Wrapper, Wrapper])) = {
    var globalSum = 0
    val (p, trie) = sc
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var sum = 0
        var i = 0
        while (i < parElems / p) {
          val v = elems(k * parElems / p + i)
          val elem = trie.remove(v)
          if (elem != null) sum += elem.value
          i += 1
        }
        globalSum += sum
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    trie
  }

  @gen("parCachetriesWithoutCounters")
  @benchmark("cache-trie.par.remove")
  @setup("parCachetrieRefill")
  @curve("no-counter")
  def cachetrieParRemoveNoCount(sc: (Int, CacheTrie[Wrapper, Wrapper])) = {
    var globalSum = 0
    val (p, trie) = sc
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var sum = 0
        var i = 0
        while (i < parElems / p) {
          val v = elems(k * parElems / p + i)
          val elem = trie.remove(v)
          if (elem != null) sum += elem.value
          i += 1
        }
        globalSum += sum
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    trie
  }

  @gen("parCachetries")
  @benchmark("cache-trie.par.remove")
  @setup("parCachetrieRefill")
  @curve("cachetrie")
  def cachetrieParRemove(sc: (Int, CacheTrie[Wrapper, Wrapper])) = {
    var globalSum = 0
    val (p, trie) = sc
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var sum = 0
        var i = 0
        while (i < parElems / p) {
          val v = elems(k * parElems / p + i)
          val elem = trie.remove(v)
          if (elem != null) sum += elem.value
          i += 1
        }
        globalSum += sum
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    trie
  }

  @gen("parCtries")
  @benchmark("cache-trie.par.remove")
  @setup("parCtrieRefill")
  @curve("ctrie")
  def ctrieParRemove(sc: (Int, TrieMap[Wrapper, Wrapper])) = {
    var globalSum = 0
    val (p, trie) = sc
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var sum = 0
        var i = 0
        while (i < parElems / p) {
          val v = elems(k * parElems / p + i)
          val elem = trie.remove(v)
          sum += elem.get.value
          i += 1
        }
        globalSum += sum
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    trie
  }

  @gen("parSkiplists")
  @benchmark("cache-trie.par.remove")
  @setup("parSkiplistRefill")
  @curve("skiplist")
  def skiplistParRemove(sc: (Int, ConcurrentSkipListMap[Wrapper, Wrapper])) = {
    var globalSum = 0
    val (p, skiplist) = sc
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var sum = 0
        var i = 0
        while (i < parElems / p) {
          val v = elems(k * parElems / p + i)
          val elem = skiplist.remove(v)
          sum += elem.value
          i += 1
        }
        globalSum += sum
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    skiplist
  }
}
