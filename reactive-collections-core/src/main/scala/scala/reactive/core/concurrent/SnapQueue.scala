package scala.reactive
package core
package concurrent



import annotation.tailrec



class SnapQueue[T] {

}


object SnapQueue {

  final class Segment[T <: AnyRef](length: Int)
  extends SegmentBase[T](length) {
    import SegmentBase._

    def capacity: Int = array.length

    @tailrec
    def enq(p: Int, x: AnyRef): Boolean = {
      if (p >= 0 && p < array.length) {
        if (CAS_ARRAY(p, EMPTY, x)) {
          WRITE_LAST(p + 1)
          true
        } else enq(findLast(p), x)
      } else false
    }

    @tailrec
    private def findLast(p: Int): Int = {
      val x = READ_ARRAY(p)
      if (x == EMPTY) p
      else if (x == FROZEN) array.length
      else if (p + 1 == array.length) p + 1
      else findLast(p + 1)
    }

    @tailrec
    def deq(): AnyRef = {
      val p = READ_HEAD()
      if (p >= 0 && p < array.length) {
        val x = READ_ARRAY(p);
        if (x == EMPTY || x == FROZEN) NONE
        else if (CAS_HEAD(p, p + 1)) x
        else deq()
      } else NONE;
    }

    def freeze() {
      freezeHead()
      freezeLast(READ_LAST())
    }

    @tailrec
    def freezeHead() {
      val p = READ_HEAD()
      if (p >= 0) {
        if (!CAS_HEAD(p, -p - 1)) freezeHead()
      }
    }

    @tailrec
    def freezeLast(p: Int) {
      if (p >= 0 && p < array.length)
        if (!CAS_ARRAY(p, EMPTY, FROZEN))
          freezeLast(findLast(p))
    }

    override def toString = s"Segment(${array.mkString(", ")})"
  }

}
