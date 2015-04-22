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

  def extractStringSupport(snapq: SnapQueue[String])
    (sup: snapq.supportOps.Support): Seq[String] = {
    val buffer = mutable.Buffer[String]()
    snapq.supportOps.foreach(sup, x => buffer += x)
    buffer
  }

  def extractStringSnapQueue(snapq: SnapQueue[String]): Seq[String] = {
    snapq.READ_ROOT() match {
      case s: snapq.Segment =>
        Util.extractStringSegment(snapq)(s)
      case r: snapq.Root =>
        val buffer = mutable.Buffer[String]()
        val lseg = r.READ_LEFT().asInstanceOf[snapq.Side].segment
        val lsup = r.READ_LEFT().asInstanceOf[snapq.Side].support
        buffer ++= Util.extractStringSegment(snapq)(lseg)
        buffer ++= Util.extractStringSupport(snapq)(lsup)
        val rseg = r.READ_RIGHT().asInstanceOf[snapq.Side].segment
        val rsup = r.READ_RIGHT().asInstanceOf[snapq.Side].support
        buffer ++= Util.extractStringSupport(snapq)(rsup)
        buffer ++= Util.extractStringSegment(snapq)(rseg)
        buffer
    }
  }

}
