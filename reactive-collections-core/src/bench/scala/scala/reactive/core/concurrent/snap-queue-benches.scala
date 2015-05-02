package scala.reactive.core
package concurrent



import java.util.concurrent.ConcurrentLinkedQueue
import org.scalameter.api._



class SnapQueueBenches extends PerformanceTest.OfflineReport {

  def sizes(from: Int, until: Int) =
    Gen.range("size")(from, until, (until - from) / 4)

  val stringSnapQueue = new SnapQueue[String]

  def emptySegs(from: Int, until: Int) = for (sz <- sizes(from, until)) yield {
    new stringSnapQueue.Segment(sz)
  }

  def fullSegs(from: Int, until: Int) = for (sz <- sizes(from, until)) yield {
    val seg = new stringSnapQueue.Segment(sz)
    Util.fillStringSegment(stringSnapQueue)(seg)
    seg
  }

  def linkedQueues(from: Int, unt: Int) = for (sz <- sizes(from, unt)) yield {
    val q = new ConcurrentLinkedQueue[String]()
    for (i <- 0 until sz) q.add("")
    (q, sz)
  }

  val opts = Context(
    exec.minWarmupRuns -> 100,
    exec.maxWarmupRuns -> 200,
    exec.benchRuns -> 60,
    exec.independentSamples -> 8
  )

  performance of "enqueue-1-thread" config(opts) in {
    val from = 100000
    val until = 500000

    using(emptySegs(from, until)) curve("Segment.enq") setUp {
      seg => seg.reinitialize()
    } in { seg =>
      var i = 0
      while (i < seg.capacity) {
        seg.enq(seg.READ_LAST(), "")
        i += 1
      }
    }

    using(emptySegs(from, until)) curve("Segment.enqueue") setUp {
      seg => seg.reinitialize()
    } in { seg =>
      var i = 0
      while (i < seg.capacity) {
        seg.enqueue("")
        i += 1
      }
    }

    using(emptySegs(from, until)) curve("SnapQueue.Segment.enqueue") setUp {
      seg => seg.reinitialize()
    } in { seg =>
      stringSnapQueue.WRITE_ROOT(seg)
      var i = 0
      while (i < seg.capacity) {
        stringSnapQueue.enqueue("")
        i += 1
      }
    }

    using(sizes(from, until)) curve("SnapQueue.Segment.alloc+enqueue") in { sz =>
      val seg = new stringSnapQueue.Segment(sz)
      stringSnapQueue.WRITE_ROOT(seg)
      var i = 0
      while (i < seg.capacity) {
        stringSnapQueue.enqueue("")
        i += 1
      }
    }

    using(sizes(from, until)) curve("SnapQueue(64).enqueue") in { sz =>
      val snapq = new SnapQueue[String](64)
      var i = 0
      while (i < sz) {
        snapq.enqueue("")
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

  performance of "dequeue-1-thread" config(opts) in {
    val from = 100000
    val until = 500000

    using(fullSegs(from, until)) curve("Segment.deq") setUp {
      seg => seg.WRITE_HEAD(0)
    } in { seg =>
      var i = 0
      while (i < seg.capacity) {
        seg.deq()
        i += 1
      }
    }

    using(linkedQueues(from, until)) curve("ConcurrentLinkedQueue") setUp {
      case (queue, sz) =>
      queue.clear()
      for (i <- 0 until sz) queue.add("")
    } in {
      case (queue, sz) =>
      var i = 0
      while (i < sz) {
        queue.poll()
        i += 1
      }
    }
  }

}
