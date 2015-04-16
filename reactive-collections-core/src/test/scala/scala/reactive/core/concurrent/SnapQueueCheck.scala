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



object SnapQueueCheck extends Properties("SnapQueue") {

  val maxSegmentSize = 2048

  val sizes = choose(0, maxSegmentSize)

  property("Segment.enq fills the segment") = forAllNoShrink(sizes) { sz =>
    val seg = new SnapQueue.Segment[String](sz)
    val insertsDone = for (i <- 0 until seg.capacity) yield {
      s"insert at $i" |: seg.enq(seg.READ_LAST(), i.toString)
    }
    val isFull = seg.READ_LAST() == seg.capacity
    val lastEnqFails = seg.enq(0, "failed") == false

    insertsDone.foldLeft("zero" |: true)(_ && _) && isFull && lastEnqFails
  }

  property("Segment.enq fills, stale 'last'") = forAllNoShrink(sizes) { sz =>
    val seg = new SnapQueue.Segment[String](sz)
    val insertsDone = for (i <- 0 until seg.capacity) yield {
      s"insert at $i" |: seg.enq(math.max(0, seg.READ_LAST() - 50), i.toString)
    }
    val isFull = "full" |: seg.READ_LAST() == seg.capacity
    val lastEnqFails = "last enq" |: seg.enq(0, "failed") == false

    insertsDone.foldLeft("zero" |: true)(_ && _) && isFull && lastEnqFails
  }

  property("Segment.enq fills half, frozen") = forAllNoShrink(sizes) { sz =>
    val seg = new SnapQueue.Segment[String](sz)
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

  def fillSegment(seg: SnapQueue.Segment[String]) {
    for (i <- 0 until seg.capacity) seg.enq(seg.READ_LAST(), i.toString)
  }

  property("Segment.deq empties the segment") = forAllNoShrink(sizes) { sz =>
    val seg = new SnapQueue.Segment[String](sz)
    fillSegment(seg)
    val removesDone = for (i <- 0 until seg.capacity) yield {
      s"remove at $i" |: seg.deq() == i.toString
    }
    val isEmpty = seg.READ_HEAD() == seg.capacity
    val lastDeqFails = seg.deq() == SegmentBase.NONE

    removesDone.foldLeft("zero" |: true)(_ && _) && isEmpty && lastDeqFails
  }

  property("Segment.deq empties half, frozen") = forAllNoShrink(sizes) { sz =>
    val seg = new SnapQueue.Segment[String](sz)
    fillSegment(seg)
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
    val seg = new SnapQueue.Segment[String](sz)
    val input = (0 until seg.capacity).map(_.toString).toArray
    val producer = Future {
      def spin() = {
        var i = 0
        while (i < delay) {
          if (seg.READ_LAST() < 0) sys.error("frozen!")
          i += 1
        }
      }
      for (i <- 0 until seg.capacity) yield {
        spin()
        s"insert at $i" |: seg.enq(seg.READ_LAST(), input(i))
      }
    }

    val consumer = Future {
      val buffer = mutable.Buffer[String]()
      while (buffer.size != seg.capacity) {
        val x = seg.deq()
        if (x != SegmentBase.NONE) {
          buffer += x.asInstanceOf[String]
        }
      }
      s"dequeued correctly: $buffer vs ${input.toSeq}" |: buffer == input.toSeq
    }

    val done = for (insertsDone <- producer; bufferGood <- consumer) yield {
      insertsDone.foldLeft("zero" |: true)(_ && _) && bufferGood
    }
    Await.result(done, Duration.Inf)
  }

}
