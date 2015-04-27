package scala.reactive
package core
package concurrent



import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._
import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.testx._



object SegmentCheck extends Properties("Segment") with ExtendedProperties {
  val unbounded = Executors.newCachedThreadPool()

  implicit val unboundedExecutorContext = ExecutionContext.fromExecutor(unbounded)

  val maxSegmentSize = 9000

  val sizes = detOneOf(value(0), value(1), detChoose(0, maxSegmentSize))

  val dummySnapQueue = new SnapQueue[String]

  val delays = detChoose(0, 10)

  val numThreads = detChoose(2, 8)

  val coarseSizes = detChoose(16, 9000)

  val fillRates = detChoose(0.0, 1.0)

  property("enq fills the segment") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val seg = new dummySnapQueue.Segment(sz)
      val insertsOk = for (i <- 0 until seg.capacity) yield {
        seg.enq(seg.READ_LAST(), i.toString)
      }
      val allInsertsGood = s"inserts ok: $insertsOk" |: insertsOk.foldLeft(true)(_ && _)
      val isFull = seg.READ_LAST() == seg.capacity
      val lastEnqFails = seg.enq(0, "failed") == false
  
      allInsertsGood && isFull && lastEnqFails
    }
  }

  property("enq fills, stale 'last'") = forAllNoShrink(sizes) { sz =>
    val seg = new dummySnapQueue.Segment(sz)
    val insertsOk = for (i <- 0 until seg.capacity) yield {
      seg.enq(math.max(0, seg.READ_LAST() - 50), i.toString)
    }
    val allInsertsGood = s"inserts ok: $insertsOk" |: insertsOk.foldLeft(true)(_ && _)
    val isFull = "full" |: seg.READ_LAST() == seg.capacity
    val lastEnqFails = "last enq" |: seg.enq(0, "failed") == false

    allInsertsGood && isFull && lastEnqFails
  }

  property("enq fills half, frozen") = forAllNoShrink(sizes) { sz =>
    val seg = new dummySnapQueue.Segment(sz)
    val insertsOk = for (i <- 0 until seg.capacity / 2) yield {
      seg.enq(seg.READ_LAST(), i.toString)
    }
    seg.freeze()
    val allInsertsGood = s"inserts ok: $insertsOk" |: insertsOk.foldLeft(true)(_ && _)
    val enqAfterFreezeFails =
      s"last enq: $seg" |: seg.enq(seg.READ_LAST(), ":(") == false
    val isFrozen = "frozen" |: seg.READ_HEAD() < 0

    allInsertsGood && isFrozen && enqAfterFreezeFails
  }

  property("deq empties the segment") = forAllNoShrink(sizes) { sz =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    val removesOk = for (i <- 0 until seg.capacity) yield {
      seg.deq() == i.toString
    }
    val allRemovesGood = s"removes ok: $removesOk" |: removesOk.foldLeft(true)(_ && _)
    val isEmpty = seg.READ_HEAD() == seg.capacity
    val lastDeqFails = seg.deq() == SegmentBase.NONE

    allRemovesGood && isEmpty && lastDeqFails
  }

  property("deq empties half, frozen") = forAllNoShrink(sizes) { sz =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    val removesOk = for (i <- 0 until seg.capacity / 2) yield {
      seg.deq() == i.toString
    }
    seg.freeze()
    val allRemovesGood = removesOk.foldLeft(true)(_ && _)
    val deqAfterFreezeFailes = "last deq" |: seg.deq() == SegmentBase.NONE
    val isFrozen = "frozen" |: seg.READ_HEAD() < 0

    allRemovesGood && isFrozen && deqAfterFreezeFailes
  }

  property("producer-consumer, varying speed") = forAllNoShrink(sizes, delays) {
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
        seg.enq(seg.READ_LAST(), input(i))
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
      // println(s"for delay $delay, maxwaits = $maxwaits")
      s"dequeued correctly: $buffer vs ${input.toSeq}" |: buffer == input.toSeq
    }

    val done = for (insertsOk <- producer; bufferGood <- consumer) yield {
      val allInsertsGood = s"inserts ok: $insertsOk" |: insertsOk.foldLeft(true)(_ && _)
      allInsertsGood && bufferGood
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

  property("freezing full disallows enqueue") = forAllNoShrink(sizes, delays) {
    (sz, delay) =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    seg.freeze()
    seg.enq(0, "") == false && seg.enq(seg.READ_LAST(), "") == false
  }

  property("freezing full disallows dequeue") = forAllNoShrink(sizes, delays) {
    (sz, delay) =>
    val seg = new dummySnapQueue.Segment(sz)
    Util.fillStringSegment(dummySnapQueue)(seg)
    seg.freeze()
    seg.deq() == SegmentBase.NONE
  }

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
    val buffers = Await.result(Future.sequence(workers), 5.seconds).toList
    val obtained = buffers.foldLeft(Seq[String]())(_ ++ _)
    (s"length: ${obtained.length}, expected: $sz" |: obtained.length == sz) &&
      (s"from $buffers, got: $obtained; expected $sz" |: obtained.toSet == inputs.toSet)
  }

  property("N producers, M consumers") =
    forAllNoShrink(coarseSizes, numThreads, numThreads) {
      (sz, n, m) =>
      stackTraced {
        val inputs = for (i <- 0 until sz) yield i.toString
        val seg = new dummySnapQueue.Segment(sz)
        val buckets = inputs.grouped(sz / n).toSeq
        val producers = for (b <- buckets) yield Future {
          for (x <- b) seg.enq(seg.READ_LAST(), x)
        }
        val counter = new AtomicInteger(0)
        val consumers = for (i <- 0 until m) yield Future {
          val buffer = mutable.Buffer[String]()
          do {
            val x = seg.deq()
            if (x != SegmentBase.NONE) {
              buffer += x.asInstanceOf[String]
              counter.incrementAndGet()
            }
          } while (counter.get < sz)
          buffer
        }
        Await.ready(Future.sequence(producers), 5.seconds)
        val buffers = Await.result(Future.sequence(consumers), 5.seconds).toList
        val obtained = buffers.foldLeft(Seq[String]())(_ ++ _)
        (s"length: ${obtained.length}, expected: $sz" |: obtained.length == sz) &&
          (s"$buffers, got: $obtained; expected $sz" |: obtained.toSet == inputs.toSet)
      }
    }

}


object SnapQueueCheck extends Properties("SnapQueue") with ExtendedProperties {
  val unbounded = Executors.newCachedThreadPool()

  implicit val unboundedExecutorContext = ExecutionContext.fromExecutor(unbounded)

  val sizes = detChoose(0, 100000)

  val fillRates = detChoose(0.0, 1.0)

  val lengths = detChoose(1, 512)

  val delays = detOneOf(value(1), detChoose(0, 16))

  val numThreads = detChoose(1, 8)

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

  property("dequeue empties segment") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val snapq = new SnapQueue[String](sz)
      snapq.READ_ROOT() match {
        case s: snapq.Segment => Util.fillStringSegment(snapq)(s)
      }
      val buffer = mutable.Buffer[String]()
      for (i <- 0 until sz) buffer += snapq.dequeue()
      s"contains input: $buffer" |: buffer == (0 until sz).map(_.toString)
    }
  }

  property("enqueue on full creates Root") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val snapq = new SnapQueue[String](sz)
      for (i <- 0 until sz) snapq.enqueue(i.toString)
      snapq.enqueue("final")
      snapq.READ_ROOT() match {
        case r: snapq.Root =>
          val lseg = r.READ_LEFT().asInstanceOf[snapq.Side].segment
          val rseg = r.READ_RIGHT().asInstanceOf[snapq.Side].segment
          val left = Util.extractStringSegment(snapq)(lseg)
          val right = Util.extractStringSegment(snapq)(rseg)
          val extracted = left ++ right
          val expected = (0 until sz).map(_.toString) :+ "final"
          s"got: $extracted" |: extracted == expected
      }
    }
  }

  property("enqueue on half-full creates Segment") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val snapq = new SnapQueue[String](sz)
      for (i <- 0 until sz) snapq.enqueue(i.toString)
      for (i <- 0 until (sz / 2 + 2)) snapq.dequeue()
      snapq.enqueue("final")
      snapq.READ_ROOT() match {
        case s: snapq.Segment =>
          val extracted = Util.extractStringSegment(snapq)(s)
          val expected = ((sz / 2 + 2) until sz).map(_.toString) :+ "final"
          s"got: $extracted" |: extracted == expected
      }
    }
  }

  property("enqueue on full works") = forAllNoShrink(sizes, lengths) {
    (sz, len) =>
    stackTraced {
      val snapq = new SnapQueue[String](len)
      for (i <- 0 until sz) snapq.enqueue(i.toString)
      val extracted = Util.extractStringSnapQueue(snapq)
      s"got: $extracted" |: extracted == (0 until sz).map(_.toString)
    }
  }

  @tailrec
  def ensureFrozen(snapq: SnapQueue[String]): Unit = {
    snapq.READ_ROOT() match {
      case f: snapq.Frozen => ensureFrozen(snapq)
      case r =>
        val fr = snapq.freeze(r, t => sys.error("no transition for test"))
        if (fr == null) ensureFrozen(snapq)
    }
  }

  property("atomic enqueue+freeze") = forAllNoShrink(sizes, lengths, delays) {
    (sz, len, delay) =>
    stackTraced {
      val buf = mutable.Queue[String]() ++= ((0 until sz).map(_.toString))
      val snapq = new SnapQueue[String](len)

      val producer = Future {
        while (buf.nonEmpty && !snapq.READ_ROOT().isInstanceOf[snapq.Frozen]) {
          val x = buf.dequeue()
          try snapq.enqueue(x)
          catch {
            case e: RuntimeException =>
              // add back to queue
              x +=: buf
          }
        }
      }

      if (len > 0) Thread.sleep(delay)
      ensureFrozen(snapq)

      Await.result(producer, 5.seconds)

      val extracted = Util.extractStringSnapQueue(snapq)
      val observed = extracted ++ buf
      s"got: $observed" |: observed == (0 until sz).map(_.toString)
    }
  }

  def subsequenceOf[T](xs: Seq[T], ys: Seq[T]): Boolean = {
    if (xs.length == 0) true
    else {
      var pos = 0
      var i = 0
      while (i < ys.length) {
        if (ys(i) == xs(pos)) pos += 1
        if (pos == xs.length) return true
        i += 1
      }
      false
    }
  }

  property("N threads can enqueue") = forAllNoShrink(sizes, lengths, numThreads) {
    (origsz, len, n) =>
    stackTraced {
      val sz = math.max(n, origsz)
      val inputs = (0 until sz).map(_.toString)
      val snapq = new SnapQueue[String](len)
      val buckets = inputs.grouped(sz / n).toSeq
      val workers = for (b <- buckets) yield Future {
        for (x <- b) snapq.enqueue(x)
      }
      Await.result(Future.sequence(workers), 5.seconds)
      val extracted = Util.extractStringSnapQueue(snapq)
      val extractedSet = extracted.toSet
      val inputSet = inputs.toSet
      val presenceLabel =
        s"diff: ${extractedSet diff inputSet}; -diff: ${inputSet diff extractedSet}"
      val allPresent = presenceLabel |: extractedSet == inputSet
      val allOrdered = (for (b <- buckets) yield {
        s"is subsequence: $b" |: subsequenceOf(b, extracted)
      }).foldLeft("zero" |: true)(_ && _)
      allPresent && allOrdered
    }
  }

  property("dequeue on full works") = forAllNoShrink(sizes, lengths) {
    (sz, len) =>
    stackTraced {
      val snapq = new SnapQueue[String](len)
      for (i <- 0 until sz) snapq.enqueue(i.toString)
      val extracted = mutable.Buffer[String]()
      @tailrec def extract() {
        val x = snapq.dequeue()
        if (x != null) {
          extracted += x
          extract()
        }
      }
      extract()

      s"got: $extracted" |: extracted == (0 until sz).map(_.toString)
    }
  }

  property("atomic dequeue+freeze") = forAllNoShrink(sizes, lengths, delays) {
    (sz, len, delay) =>
    stackTraced {
      val snapq = new SnapQueue[String](len)
      for (i <- 0 until sz) snapq.enqueue(i.toString)

      val consumer = Future {
        val extracted = mutable.Buffer[String]()
        @tailrec def extract() {
          val x = try {
            snapq.dequeue()
          } catch {
            case e: RuntimeException => null
          }
          if (x != null) {
            extracted += x.asInstanceOf[String]
            extract()
          }
        }
        extract()
        extracted
      }

      if (delay > 0) Thread.sleep(delay)
      ensureFrozen(snapq)

      val extracted = Await.result(consumer, 5.seconds)
      val leftover = Util.extractStringSnapQueue(snapq)
      val observed = extracted ++ leftover
      s"got: $observed" |: observed == (0 until sz).map(_.toString)
    }
  }

  property("N threads can dequeue") = forAllNoShrink(sizes, lengths, numThreads) {
    (sz, len, n) =>
    stackTraced {
      val inputs = (0 until sz).map(_.toString)
      val snapq = new SnapQueue[String](len)
      for (x <- inputs) snapq.enqueue(x)
      val workers = for (i <- 0 until n) yield Future {
        val extracted = mutable.Buffer[String]()
        @tailrec def extract() {
          val x = snapq.dequeue()
          if (x != null) {
            extracted += x
            extract()
          }
        }
        extract()
        extracted
      }
      val buffers = Await.result(Future.sequence(workers), 5.seconds)
      val extracted = buffers.foldLeft(Seq[String]())(_ ++ _)
      val extractedSet = extracted.toSet
      val inputSet = inputs.toSet
      val presenceLabel =
        s"diff: ${extractedSet diff inputSet}; -diff: ${inputSet diff extractedSet}"
      val allPresent = presenceLabel |: extractedSet == inputSet
      val allOrdered = (for (b <- buffers) yield {
        s"is subsequence: $b" |: subsequenceOf(b, inputs)
      }).foldLeft("zero" |: true)(_ && _)
      allPresent && allOrdered
    }
  }

  property("producer-consumer, with delays") = forAllNoShrink(sizes, lengths, delays) {
    (sz, len, delay) =>
    stackTraced {
      val snapq = new SnapQueue[String](len)

      val producer = Future {
        for (i <- 0 until sz) snapq.enqueue(i.toString)
      }

      val consumer = Future {
        def spin() {
          var i = 0
          while (i < delay) {
            snapq.READ_ROOT()
            i += 1
          }
        }
        val extracted = mutable.Buffer[String]()
        while (extracted.size != sz) {
          spin()
          val x = snapq.dequeue()
          if (x != null) extracted += x
        }
        extracted
      }

      val extracted = Await.result(consumer, 3.seconds)
      s"got: $extracted" |: extracted == (0 until sz).map(_.toString)
    }
  }

  property("N producer, M consumers") =
    forAllNoShrink(sizes, lengths, numThreads, numThreads) {
      (origsz, len, n, m) =>
      stackTraced {
        val sz = math.max(n, origsz)
        val inputs = for (i <- 0 until sz) yield i.toString
        val snapq = new SnapQueue[String](len)
        val buckets = inputs.grouped(sz / n).toSeq
  
        val producers = for (b <- buckets) yield Future {
          for (x <- b) snapq.enqueue(x)
        }
  
        val counter = new AtomicInteger(0)
  
        val consumers = for (i <- 0 until m) yield Future {
          val buffer = mutable.Buffer[String]()
          do {
            val x = snapq.dequeue()
            if (x != null) {
              buffer += x.asInstanceOf[String]
              counter.incrementAndGet()
            }
          } while (counter.get < sz)
          buffer
        }

        Await.ready(Future.sequence(producers), 5.seconds)
        val buffers = Await.result(Future.sequence(consumers), 5.seconds).toList
        val obtained = buffers.foldLeft(Seq[String]())(_ ++ _)
          
        (s"length: ${obtained.length}, expected: $sz" |: obtained.length == sz) &&
          (s"$buffers, got: $obtained; expected $sz" |: obtained.toSet == inputs.toSet)
      }
    }

}
