package io.reactors.common.concurrent



import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import org.scalameter.api._
import org.scalameter.execution.LocalExecutor
import org.scalameter.japi.JBench
import org.scalatest.FunSuite
import scala.collection.concurrent.TrieMap
import scala.util.Random



class CacheTrieRemoveBenches extends JBench.OfflineReport {
  override def historian =
    org.scalameter.reporting.RegressionReporter.Historian.Complete()

  override def defaultConfig = Context(
    exec.minWarmupRuns -> 40,
    exec.maxWarmupRuns -> 60,
    exec.independentSamples -> 1,
    exec.jvmflags -> List("-server", "-verbose:gc", "-Xmx6092m", "-Xms6092m"),
    verbose -> true
  )

  case class Wrapper(value: Int) extends Comparable[Wrapper] {
    def compareTo(that: Wrapper) = this.value - that.value
  }

  val maxElems = 500000

  @transient
  lazy val elems = Random.shuffle((0 until maxElems).toVector)
    .map(i => Wrapper(i)).toArray

  val sizes = Gen.range("size")(50000, maxElems, 100000)

  val pars = Gen.exponential("pars")(1, 8, 2)

  val parElems = 100000

  val parCachetries = for (p <- pars) yield {
    val trie = new CacheTrie[Wrapper, Wrapper]
    for (i <- 0 until parElems) trie.insert(elems(i), elems(i))
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
  }

  @gen("chms")
  @benchmark("cache-trie.remove")
  @setup("chmRefill")
  @curve("CHM")
  def chmRemove(sc: (Int, ConcurrentHashMap[Wrapper, Wrapper])): Int = {
    val (size, chm) = sc
    var i = 0
    var sum = 0
    while (i < size) {
      sum += chm.remove(elems(i)).value
      i += 1
    }
    sum
  }

  @gen("skiplists")
  @benchmark("cache-trie.remove")
  @setup("skiplistRefill")
  @curve("skiplist")
  def skiplistRemove(sc: (Int, ConcurrentSkipListMap[Wrapper, Wrapper])): Int = {
    val (size, skiplist) = sc
    var i = 0
    var sum = 0
    while (i < size) {
      sum += skiplist.remove(elems(i)).value
      i += 1
    }
    sum
  }

  @gen("ctries")
  @benchmark("cache-trie.remove")
  @setup("ctrieRefill")
  @curve("ctrie")
  def ctrieRemove(sc: (Int, TrieMap[Wrapper, Wrapper])): Int = {
    val (size, trie) = sc
    var i = 0
    var sum = 0
    while (i < size) {
      sum += trie.remove(elems(i)).get.value
      i += 1
    }
    sum
  }

  @gen("cachetries")
  @benchmark("cache-trie.remove")
  @setup("cachetrieRefill")
  @curve("cachetrie")
  def cachetrieRemove(sc: (Int, CacheTrie[Wrapper, Wrapper])): Int = {
    val (size, trie) = sc
    var i = 0
    var sum = 0
    while (i < size) {
      sum += trie.remove(elems(i)).value
      i += 1
    }
    // println(trie.debugCacheStats)
    sum
  }
}
