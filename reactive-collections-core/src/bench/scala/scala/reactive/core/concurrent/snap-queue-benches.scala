package scala.reactive.core
package concurrent



import java.util.concurrent.ConcurrentLinkedQueue
import org.scalameter.api._



trait SnapQueueBench extends PerformanceTest.OfflineReport {

  def sizes(from: Int, until: Int) =
    Gen.range("size")(from, until, (until - from) / 4)

  def parallelisms(from: Int, until: Int) =
    Gen.range("parallelism")(from, until, 1)

  val stringSnapQueue = new SnapQueue[String]

  def emptySegs(from: Int, until: Int) = for (sz <- sizes(from, until)) yield {
    new stringSnapQueue.Segment(sz)
  }

  def fullSegs(from: Int, until: Int) = for (sz <- sizes(from, until)) yield {
    val seg = new stringSnapQueue.Segment(sz)
    Util.fillStringSegment(stringSnapQueue)(seg)
    seg
  }

  def emptySnapQueue(len: Int, from: Int, until: Int) =
    for (sz <- sizes(from, until)) yield (new SnapQueue[String](), sz)

  def linkedQueues(from: Int, unt: Int) = for (sz <- sizes(from, unt)) yield {
    val q = new ConcurrentLinkedQueue[String]()
    (q, sz)
  }

  def linkedQueueParallelisms(parFrom: Int, parUntil: Int) = {
    for (p <- parallelisms(parFrom, parUntil)) yield {
      val q = new ConcurrentLinkedQueue[String]()
      (q, p)
    }
  }

  def emptySnapQueueParallelisms(len: Int, parFrom: Int, parUntil: Int) = {
    for (p <- parallelisms(parFrom, parUntil)) yield {
      val q = new SnapQueue[String](len)
      (q, p)
    }
  }

  def opts = Context(
    exec.minWarmupRuns -> 50,
    exec.maxWarmupRuns -> 100,
    exec.benchRuns -> 60,
    exec.independentSamples -> 4
  )

}


class SnapQueueProducerBench extends SnapQueueBench {

  performance of "enqueue-1-thread" config(opts) in {
    val from = 100000
    val until = 500000
    val len = 64

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

    using(sizes(from, until)) curve(s"SnapQueue($len).enqueue") in { sz =>
      val snapq = new SnapQueue[String](len)
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

}


class SnapQueueMultipleProducerBench extends SnapQueueBench {

  override def opts = Context(
    exec.minWarmupRuns -> 50,
    exec.maxWarmupRuns -> 100,
    exec.benchRuns -> 40,
    exec.independentSamples -> 1
  )

  performance of "enqueue-N-threads" config(opts) in {
    val size = 500000
    val parFrom = 1
    val parUntil = 8
    val len = 64

    using(parallelisms(parFrom, parUntil)) curve("ConcurrentLinkedQueue") in { par =>
      val queue = new ConcurrentLinkedQueue[String]
      val batchSize = size / par
      val threads = for (i <- 0 until par) yield new Thread {
        override def run() {
          var i = 0
          while (i < batchSize) {
            queue.add("")
            i += 1
          }
        }
      }
      for (t <- threads) t.start()
      for (t <- threads) t.join()
    }

    using(parallelisms(parFrom, parUntil)) curve(s"SnapQueue($len)") in { par =>
      val snapq = new SnapQueue[String](len)
      val batchSize = size / par
      val threads = for (i <- 0 until par) yield new Thread {
        override def run() {
          var i = 0
          while (i < batchSize) {
            snapq.enqueue("")
            i += 1
          }
        }
      }
      for (t <- threads) t.start()
      for (t <- threads) t.join()
    }
  }

}


class SnapQueueConsumerBench extends SnapQueueBench {

  performance of "dequeue-1-thread" config(opts) in {
    val from = 100000
    val until = 500000
    val len = 64

    // using(fullSegs(from, until)) curve("Segment.deq") setUp {
    //   seg => seg.WRITE_HEAD(0)
    // } in { seg =>
    //   var i = 0
    //   while (i < seg.capacity) {
    //     seg.deq()
    //     i += 1
    //   }
    // }

    // using(emptySnapQueue(len, from, until)) curve(s"SnapQueue($len).dequeue") setUp {
    //   case (q, sz) => for (i <- 0 until sz) q.enqueue("")
    // } in {
    //   case (q, sz) =>
    //   var i = 0
    //   while (i < sz) {
    //     q.dequeue()
    //     i += 1
    //   }
    // }

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
        var j = 0
        while (j < 25) {
          java.util.concurrent.ThreadLocalRandom.current().nextInt()
          j += 1
        }
      }
    }
  }

}


class SnapQueueMultipleConsumerBench extends SnapQueueBench {

  override def opts = Context(
    //exec.jvmflags -> "-XX:+UseCondCardmark",
    exec.jvmflags -> "-XX:+UseG1GC",
    exec.minWarmupRuns -> 40,
    exec.maxWarmupRuns -> 80,
    exec.benchRuns -> 40,
    exec.independentSamples -> 6
  )

  performance of "dequeue-N-threads" config(opts) in {
    val size = 500000
    val parFrom = 1
    val parUntil = 8
    val len = 64

    using(linkedQueueParallelisms(parFrom, parUntil)).curve("ConcurrentLinkedQueue")
      .setUp {
      case (queue, _) =>
      queue.clear()
      for (i <- 0 until size) queue.add("")
    } in { case (queue, par) =>
      val batchSize = size / par
      val threads = for (i <- 0 until par) yield new Thread {
        override def run() {
          var i = 0
          while (i < batchSize) {
            queue.poll()
            i += 1
          }
        }
      }
      for (t <- threads) t.start()
      for (t <- threads) t.join()
    }

    using(emptySnapQueueParallelisms(len, parFrom, parUntil))
      .curve(s"SnapQueue($len).dequeue").setUp {
      case (snapq, _) => for (i <- 0 until size) snapq.enqueue("")
    } in { case (snapq, par) =>
      val batchSize = size / par
      val threads = for (i <- 0 until par) yield new Thread {
        override def run() {
          var i = 0
          while (i < batchSize) {
            snapq.dequeue()
            i += 1
          }
        }
      }
      for (t <- threads) t.start()
      for (t <- threads) t.join()
    }
  }

}


class SnapQueueProducerConsumerBench extends SnapQueueBench {

  override def opts = Context(
    exec.minWarmupRuns -> 30,
    exec.maxWarmupRuns -> 60,
    exec.benchRuns -> 40,
    exec.independentSamples -> 8
  )

  performance of "1-producer-1-consumer" config(opts) in {
    val from = 100000
    val until = 500000
    val len = 64

    using(sizes(from, until)) curve("ConcurrentLinkedQueue") in { size =>
      val queue = new ConcurrentLinkedQueue[String]
      val producer = new Thread {
        override def run() {
          var i = 0
          while (i < size) {
            queue.add("")
            i += 1
          }
        }
      }
      val consumer = new Thread {
        override def run() {
          var i = 0
          while (i < size) {
            val x = queue.poll()
            if (x != null) {
              i += 1
            }
            var j = 0
            while (j < 25) {
              java.util.concurrent.ThreadLocalRandom.current().nextInt()
              j += 1
            }
          }
        }
      }
      producer.start()
      consumer.start()
      producer.join()
      consumer.join()
    }

    using(sizes(from, until)) curve(s"SnapQueue($len)") in { size =>
      val snapq = new SnapQueue[String](len)
      val producer = new Thread {
        override def run() {
          var i = 0
          while (i < size) {
            snapq.enqueue("")
            i += 1
          }
        }
      }
      val consumer = new Thread {
        override def run() {
          var i = 0
          while (i < size) {
            val x = snapq.dequeue()
            if (x != null) {
              i += 1
            }
          }
        }
      }
      producer.start()
      consumer.start()
      producer.join()
      consumer.join()
    }

  }

}
