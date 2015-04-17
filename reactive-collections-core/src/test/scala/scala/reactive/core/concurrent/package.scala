package scala.reactive.core






package object concurrent {

  def fillStringSegment(seg: SnapQueue.Segment[String]) {
    for (i <- 0 until seg.capacity) seg.enq(seg.READ_LAST(), i.toString)
  }

}
