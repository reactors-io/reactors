package scala.reactive
package core
package concurrent



import org.scalacheck._
import org.scalacheck.Prop._
import org.scalacheck.Gen._



object SnapQueueCheck extends Properties("SnapQueue") {

  val maxSegmentSize = 2048

  val emptySegments = for (len <- choose(0, maxSegmentSize)) yield {
    new SnapQueue.Segment[String](len)
  }

  property("Segment.enq fills the segment") = forAll(emptySegments) { seg =>
    val insertsDone = for (i <- 0 until seg.capacity) yield {
      s"insert at $i" |: seg.enq(seg.READ_LAST(), i.toString)
    }
    val isFull = seg.READ_LAST() == seg.capacity
    val lastEnqFails = seg.enq(0, "failed") == false

    insertsDone.foldLeft("zero" |: true)(_ && _) && isFull && lastEnqFails
  }

  property("Segment.enq fills, stale 'last'") = forAll(emptySegments) { seg =>
    val insertsDone = for (i <- 0 until seg.capacity) yield {
      s"insert at $i" |: seg.enq(math.max(0, seg.READ_LAST() - 50), i.toString)
    }
    val isFull = seg.READ_LAST() == seg.capacity
    val lastEnqFails = seg.enq(0, "failed") == false

    insertsDone.foldLeft("zero" |: true)(_ && _) && isFull && lastEnqFails
  }

  property("Segment.enq fills half, frozen") = forAll(emptySegments) { seg =>
    val insertsDone = for (i <- 0 until seg.capacity / 2) yield {
      s"insert at $i" |: seg.enq(seg.READ_LAST(), i.toString)
    }
    seg.freeze()
    val enqAfterFreezeFails = seg.enq(seg.READ_LAST(), "failed")
    val isFrozen = seg.READ_HEAD() < 0

    insertsDone.foldLeft("zero" |: true)(_ && _) && isFrozen &&
      enqAfterFreezeFails
  }

  def fillSegment(seg: SnapQueue.Segment[String]) {
    for (i <- 0 until seg.capacity) seg.enq(seg.READ_LAST(), i.toString)
  }

  property("Segment.deq empties the segment") = forAll(emptySegments) { seg =>
    fillSegment(seg)
    val removesDone = for (i <- 0 until seg.capacity) yield {
      s"remove at $i" |: seg.deq() == i.toString
    }
    val isEmpty = seg.READ_HEAD() == seg.capacity
    val lastDeqFails = seg.deq() == SegmentBase.NONE

    removesDone.foldLeft("zero" |: true)(_ && _) && isEmpty && lastDeqFails
  }

}
