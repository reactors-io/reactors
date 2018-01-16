package io.reactors.common.concurrent



import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import org.scalameter.api._
import org.scalameter.execution.LocalExecutor
import org.scalameter.japi.JBench
import org.scalatest.FunSuite
import scala.collection.concurrent.TrieMap
import scala.util.Random



class HighContentionBenches extends JBench.OfflineReport {
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

  val maxElems = 600000

  @transient
  lazy val elems = Random.shuffle((0 until maxElems).toVector)
    .map(i => Wrapper(i)).toArray

  val sizes = Gen.range("size")(50000, maxElems, 100000)

  val pars = Gen.exponential("pars")(1, 8, 2)

  val parElems = 600000

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

  @gen("pars")
  @benchmark("cache-trie.par.insert")
  @curve("chm")
  def chmParInsert(p: Int) = {
    val chm = new ConcurrentHashMap[Wrapper, Wrapper]
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var i = 0
        while (i < parElems / p) {
          val v = elems(i)
          chm.put(v, v)
          i += 1
        }
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    chm
  }

  @gen("pars")
  @benchmark("cache-trie.par.insert")
  @curve("ctrie")
  def ctrieParInsert(p: Int) = {
    val ctrie = new TrieMap[Wrapper, Wrapper]
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var i = 0
        while (i < parElems / p) {
          val v = elems(i)
          ctrie.put(v, v)
          i += 1
        }
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    ctrie
  }

  @gen("pars")
  @benchmark("cache-trie.par.insert")
  @curve("skiplist")
  def skiplistParInsert(p: Int) = {
    val skiplist = new ConcurrentSkipListMap[Wrapper, Wrapper]
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var i = 0
        while (i < parElems / p) {
          val v = elems(i)
          skiplist.put(v, v)
          i += 1
        }
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    skiplist
  }

  @gen("pars")
  @benchmark("cache-trie.par.insert")
  @curve("cachetrie")
  def cachetrieParInsert(p: Int) = {
    val trie = new CacheTrie[Wrapper, Wrapper]
    val threads = for (k <- 0 until p) yield new Thread {
      override def run(): Unit = {
        var i = 0
        while (i < parElems / p) {
          val v = elems(i)
          trie.insert(v, v)
          i += 1
        }
      }
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    // println(trie.debugCacheStats)
    trie
  }
}
