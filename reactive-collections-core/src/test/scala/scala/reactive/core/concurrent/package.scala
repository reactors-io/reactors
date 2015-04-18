package scala.reactive.core






package object concurrent {

  def fillStringSegment(dummy: SnapQueue[String])(seg: dummy.Segment) {
    for (i <- 0 until seg.capacity) seg.enq(seg.READ_LAST(), i.toString)
  }

}
