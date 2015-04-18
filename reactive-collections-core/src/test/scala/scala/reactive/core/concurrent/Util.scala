package scala.reactive.core
package concurrent



import scala.collection._



object Util {

  def fillStringSegment(dummy: SnapQueue[String])(seg: dummy.Segment) {
    for (i <- 0 until seg.capacity) seg.enq(seg.READ_LAST(), i.toString)
  }

  def extractStringSegment(dummy: SnapQueue[String])(seg: dummy.Segment):
    Seq[String] = {
    val buffer = mutable.Buffer[String]()
    do {
      val x = seg.deq()
      if (x != SegmentBase.NONE) buffer += x.asInstanceOf[String]
      else return buffer
    } while (true)
    ???
  }

}
