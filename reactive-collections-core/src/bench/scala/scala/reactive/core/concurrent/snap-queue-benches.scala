package scala.reactive.core
package concurrent



import java.util.concurrent.ConcurrentLinkedQueue
import org.scalameter.api._



class SnapQueueBenches extends PerformanceTest.OfflineReport {

  def sizes(from: Int, until: Int) =
    Gen.range("size")(from, until, (until - from) / 4)

  def emptySegs(from: Int, until: Int) = for (sz <- sizes(from, until)) yield {
    new SnapQueue.Segment[String](sz)
  }

  def fullSegs(from: Int, until: Int) = for (sz <- sizes(from, until)) yield {
    val seg = new SnapQueue.Segment[String](sz)
    fillStringSegment(seg)
    seg
  }

  def linkedQueues(from: Int, unt: Int) = for (sz <- sizes(from, unt)) yield {
    val q = new ConcurrentLinkedQueue[String]()
    for (i <- 0 until sz) q.add("")
    (q, sz)
  }

  val opts = Context(
    exec.minWarmupRuns -> 45,
    exec.maxWarmupRuns -> 90,
    exec.benchRuns -> 60,
    exec.independentSamples -> 3
  )

  performance of "enq-1-thread" config(opts) in {
    val from = 100000
    val until = 500000

    using(emptySegs(from, until)) curve("Segment") in { seg =>
      var i = 0
      while (i < seg.capacity) {
        seg.enq(seg.READ_LAST(), "")
        i += 1
      }
    }

    using(sizes(from, until)) curve("ConcurrentLinkedQueue") in { sz =>
      val queue = new ConcurrentLinkedQueue[String]
      var i = 0
      while (i < sz) {
        queue.add("")
        i += 1
      }
    }
  }

  performance of "deq-1-thread" config(opts) in {
    val from = 100000
    val until = 500000

    using(fullSegs(from, until)) curve("Segment") in { seg =>
      var i = 0
      while (i < seg.capacity) {
        seg.deq()
        i += 1
      }
    }

    using(linkedQueues(from, until)) curve("ConcurrentLinkedQueue") in {
      case (queue, sz) =>
      var i = 0
      while (i < sz) {
        queue.poll()
        i += 1
      }
    }
  }

}
