package scala.reactive
package core
package concurrent



import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._



trait SnapQueueUtils {
  def stackTraced[T](p: =>T): T = {
    try {
      p
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }
  }
}


object SegmentCheck extends Properties("Segment") with SnapQueueUtils {
  val maxSegmentSize = 3250

  val sizes = oneOf(value(0), value(1), choose(0, maxSegmentSize))

  val dummySnapQueue = new SnapQueue[String]

  property("enq fills the segment") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val seg = new dummySnapQueue.Segment(sz)
      val insertsDone = for (i <- 0 until seg.capacity) yield {
        s"insert at $i" |: seg.enq(seg.READ_LAST(), i.toString)
      }
      val isFull = seg.READ_LAST() == seg.capacity
      val lastEnqFails = seg.enq(0, "failed") == false
  
      insertsDone.foldLeft("zero" |: true)(_ && _) && isFull && lastEnqFails
    }
  }

  property("enq fills, stale 'last'") = forAllNoShrink(sizes) { sz =>
    val seg = new dummySnapQueue.Segment(sz)
    val insertsDone = for (i <- 0 until seg.capacity) yield {
      s"insert at $i" |: seg.enq(math.max(0, seg.READ_LAST() - 50), i.toString)
    }
    val isFull = "full" |: seg.READ_LAST() == seg.capacity
    val lastEnqFails = "last enq" |: seg.enq(0, "failed") == false

    insertsDone.foldLeft("zero" |: true)(_ && _) && isFull && lastEnqFails
  }

  property("enq fills half, frozen") = forAllNoShrink(sizes) { sz =>
    val seg = new dummySnapQueue.Segment(sz)
    val insertsDone = for (i <- 0 until seg.capacity / 2) yield {
      s"insert at $i" |: seg.enq(seg.READ_LAST(), i.toString)
    }
    seg.freeze()
    val enqAfterFreezeFails =
      s"last enq: $seg" |: seg.enq(seg.READ_LAST(), ":(") == false
    val isFrozen = "frozen" |: seg.READ_HEAD() < 0

    insertsDone.foldLeft("zero" |: true)(_ && _) && isFrozen &&
      enqAfterFreezeFails
  }

  property("deq empties the segment") = forAllNoShrink(sizes) { sz =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    val removesDone = for (i <- 0 until seg.capacity) yield {
      s"remove at $i" |: seg.deq() == i.toString
    }
    val isEmpty = seg.READ_HEAD() == seg.capacity
    val lastDeqFails = seg.deq() == SegmentBase.NONE

    removesDone.foldLeft("zero" |: true)(_ && _) && isEmpty && lastDeqFails
  }

  property("deq empties half, frozen") = forAllNoShrink(sizes) { sz =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    val removesDone = for (i <- 0 until seg.capacity / 2) yield {
      s"remove at $i" |: seg.deq() == i.toString
    }
    seg.freeze()
    val deqAfterFreezeFailes = "last deq" |: seg.deq() == SegmentBase.NONE
    val isFrozen = "frozen" |: seg.READ_HEAD() < 0

    removesDone.foldLeft("zero" |: true)(_ && _) && isFrozen &&
      deqAfterFreezeFailes
  }

  val delays = choose(0, 10)

  property("Producer-consumer, varying speed") = forAllNoShrink(sizes, delays) {
    (sz, delay) =>
    val seg = new dummySnapQueue.Segment(sz)
    val input = (0 until seg.capacity).map(_.toString).toArray
    val producer = Future {
      def spin() = {
        var i = 0
        while (i < delay) {
          if (seg.READ_HEAD() < 0) sys.error("frozen!")
          i += 1
        }
      }
      for (i <- 0 until seg.capacity) yield {
        spin()
        s"insert at $i" |: seg.enq(seg.READ_LAST(), input(i))
      }
    }

    val consumer = Future {
      var waits = 0
      var maxwaits = 0
      val buffer = mutable.Buffer[String]()
      while (buffer.size != seg.capacity) {
        val x = seg.deq()
        if (x != SegmentBase.NONE) {
          maxwaits = math.max(waits, maxwaits)
          waits = 0
          buffer += x.asInstanceOf[String]
        } else waits += 1
      }
      //println(s"for delay $delay, maxwaits = $maxwaits")
      s"dequeued correctly: $buffer vs ${input.toSeq}" |: buffer == input.toSeq
    }

    val done = for (insertsDone <- producer; bufferGood <- consumer) yield {
      insertsDone.foldLeft("zero" |: true)(_ && _) && bufferGood
    }
    Await.result(done, Duration.Inf)
  }

  property("Consumer sees prefix when frozen") = forAllNoShrink(sizes, delays) {
    (sz, delay) =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)

    val consumer = Future {
      def spin(): Boolean = {
        var i = 0
        var frozen = false
        do {
          if (seg.READ_HEAD() < 0) frozen = true
          i += 1
        } while (i < delay)
        frozen
      }
      val buffer = mutable.Buffer[String]()
      while (!spin() && buffer.size < seg.capacity) {
        val x = seg.deq()
        if (x != SegmentBase.NONE) buffer += x.asInstanceOf[String]
      }
      buffer
    }

    val freezer = Future {
      seg.freeze()
    }

    val done = for (_ <- freezer; prefix <- consumer) yield {
      s"seen some prefix: $prefix" |:
        prefix == (0 until seg.capacity).map(_.toString).take(prefix.length)
    }
    Await.result(done, Duration.Inf)
  }

  property("Freezing full disallows enqueue") = forAllNoShrink(sizes, delays) {
    (sz, delay) =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    seg.freeze()
    seg.enq(0, "") == false && seg.enq(seg.READ_LAST(), "") == false
  }

  property("Freezing full disallows dequeue") = forAllNoShrink(sizes, delays) {
    (sz, delay) =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    seg.freeze()
    seg.deq() == SegmentBase.NONE
  }

  val fillRates = choose(0.0, 1.0)

  property("locateHead after freeze") = forAllNoShrink(sizes, fillRates) {
    (sz, fill) =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    val total = (sz * fill).toInt
    for (i <- 0 until total) seg.deq()
    seg.freeze()
    val locatedHead = seg.locateHead
    s"$locatedHead vs $total" |: locatedHead == total
  }

  property("locateLast after freeze") = forAllNoShrink(sizes, fillRates) {
    (sz, fill) =>
    val seg = new dummySnapQueue.Segment(sz)
    val total = (sz * fill).toInt
    for (i <- 0 until total) seg.enq(seg.READ_LAST(), i.toString)
    seg.freeze()
    val locatedLast = seg.locateLast
    s"$locatedLast vs $total" |: locatedLast == total
  }

  property("locateLast after stale freeze") = forAllNoShrink(sizes, fillRates) {
    (sz, fill) =>
    val seg = new dummySnapQueue.Segment(sz)
    val total = (sz * fill).toInt
    for (i <- 0 until total) seg.enq(seg.READ_LAST(), i.toString)
    seg.freeze()
    seg.WRITE_LAST(0)
    val locatedLast = seg.locateLast
    s"$locatedLast vs $total" |: locatedLast == total
  }

  property("copyShift after deq") = forAllNoShrink(sizes, fillRates) {
    (sz, fill) =>
    stackTraced {
      val seg = new dummySnapQueue.Segment(sz)
      Util.fillStringSegment(dummySnapQueue)(seg)
      val total = (sz * fill).toInt
      for (i <- 0 until total) seg.deq()
      seg.freeze()
      val nseg = seg.copyShift()
      val extracted = Util.extractStringSegment(dummySnapQueue)(nseg)
      s"should contain from $total until $sz: $nseg" |:
        extracted == (total until sz).map(_.toString)
    }
  }

  property("copyShift after enq") = forAllNoShrink(sizes, fillRates) {
    (sz, fill) =>
    val seg = new dummySnapQueue.Segment(sz)
    val total = (sz * fill).toInt
    for (i <- 0 until total) seg.enq(seg.READ_LAST(), i.toString)
    seg.freeze()
    val nseg = seg.copyShift()
    val extracted = Util.extractStringSegment(dummySnapQueue)(nseg)
    s"should contain from 0 until $total: $nseg" |:
      extracted == (0 until total).map(_.toString)
  }

  property("unfreeze after deq") = forAllNoShrink(sizes, fillRates) {
    (sz, fill) =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    val total = (sz * fill).toInt
    for (i <- 0 until total) seg.deq()
    seg.freeze()
    val nseg = seg.unfreeze()
    val extracted = Util.extractStringSegment(dummySnapQueue)(nseg)
    s"should contain from $total until $sz: $nseg" |:
      extracted == (total until sz).map(_.toString)
  }

  property("unfreeze after enq") = forAllNoShrink(sizes, fillRates) {
    (sz, fill) =>
    val seg = new dummySnapQueue.Segment(sz)
    val total = (sz * fill).toInt
    for (i <- 0 until total) seg.enq(seg.READ_LAST(), i.toString)
    seg.freeze()
    val nseg = seg.unfreeze()
    val extracted = Util.extractStringSegment(dummySnapQueue)(nseg)
    s"should contain from 0 until $total: $nseg" |:
      extracted == (0 until total).map(_.toString)
  }

  val numThreads = choose(2, 8)

  val coarseSizes = choose(16, 9000)

  property("N threads can enqueue") = forAllNoShrink(coarseSizes, numThreads) {
    (sz, n) =>
    val inputs = (0 until sz).map(_.toString)
    val seg = new dummySnapQueue.Segment(sz)
    val buckets = inputs.grouped(sz / n).toSeq
    val workers = for (bucket <- buckets) yield Future {
      stackTraced {
        var i = 0
        var failing = -1
        for (x <- bucket) {
          if (!seg.enqueue(x)) failing = i
          i += 1
        }
        failing
      }
    }
    val failures = Await.result(Future.sequence(workers), Duration.Inf).toList
    val extracted = Util.extractStringSegment(dummySnapQueue)(seg)
    s"no failures: $failures" |: failures.forall(_ == -1) &&
      extracted.toSet == inputs.toSet
  }

  property("N threads can dequeue") = forAllNoShrink(coarseSizes, numThreads) {
    (sz, n) =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    val inputs = (0 until sz).map(_.toString)
    val workers = for (i <- 0 until n) yield Future {
      stackTraced {
        val buffer = mutable.Buffer[String]()
        var stop = false
        do {
          val x = seg.deq()
          if (x != SegmentBase.NONE) buffer += x.asInstanceOf[String]
          else stop = true
        } while (!stop)
        buffer
      }
    }
    val buffers = Await.result(Future.sequence(workers), Duration.Inf).toList
    val obtained = buffers.foldLeft(Seq[String]())(_ ++ _)
    (s"lengths: ${obtained.length}, expected: $sz" |: obtained.length == sz) &&
      (s"$buffers: $obtained; size $sz" |: obtained.toSet == inputs.toSet)
  }

}


object SnapQueueCheck extends Properties("SnapQueue") with SnapQueueUtils {
  val sizes = choose(0, 100000)

  val fillRates = choose(0.0, 1.0)

  property("enqueue fills segment") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val snapq = new SnapQueue[String](sz)
      for (i <- 0 until sz) snapq.enqueue(i.toString)
      snapq.READ_ROOT() match {
        case s: snapq.Segment =>
          Util.extractStringSegment(snapq)(s) == (0 until sz).map(_.toString)
      }
    }
  }

  property("freeze freezes segment") = forAllNoShrink(sizes, fillRates) {
    (sz, fillRate) =>
    stackTraced {
      val snapq = new SnapQueue[String](sz)
      val total = (sz * fillRate).toInt
      for (i <- 0 until total) snapq.enqueue(i.toString)
      assert(snapq.freeze(snapq.READ_ROOT(), null) != null)
      snapq.READ_ROOT() match {
        case f: snapq.Frozen => f.root match {
          case s: snapq.Segment => s.READ_HEAD() < 0
        }
      }
    }
  }

}
