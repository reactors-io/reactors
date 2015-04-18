package scala.reactive
package core
package concurrent



import annotation.tailrec
import annotation.unchecked



class SnapQueue[T](val L: Int = 128)
  (implicit val supportOps: SnapQueue.SupportOps[T])
extends SnapQueueBase[T] {
  import SnapQueue.Trans

  private[concurrent] def transition(r: RootOrSegmentOrFrozen[T],
    f: Trans[T]): RootOrSegmentOrFrozen[T] = {
    ???
  }

  def expand[T](r: RootOrSegmentOrFrozen[T]): RootOrSegmentOrFrozen[T] = {
    (r: @unchecked) match {
      case s: Segment =>
        // val head = s.locateHead
        // val last = s.locateLast
        // if (last - head < s.array.length / 2) {
        //   copy(s)
        // } else {
        //   val left = new Side(false, unfreeze(s), supportOps.create())
        //   val right = new Side(false, new Segment(L))
        //   new Root(left, right)
        // }
        ???
    }
  }

  final class Frozen(val f: Trans[T], val root: RootOrSegmentOrFrozen[T])
  extends RootOrSegmentOrFrozen[T] {
    def enqueue(x: T): Boolean = ???
    def dequeue(): Object = ???
  }

  final class Root(l: Side, r: Side) extends RootBase[T] {
    WRITE_LEFT(l)
    WRITE_RIGHT(r)
    def enqueue(x: T): Boolean = ???
    def dequeue(): Object = ???
  }

  final class Side(
    val isFrozen: Boolean,
    val segment: Segment,
    val support: AnyRef
  ) extends SideBase[T]

  final class Segment(length: Int)
  extends SegmentBase[T](length) {
    import SegmentBase._

    def capacity: Int = array.length

    def enqueue(x: T): Boolean = {
      val p = READ_LAST()
      if (enq(p, x.asInstanceOf[AnyRef])) true
      else {
        if (READ_HEAD() < 0) false // frozen
        else { // full
          SnapQueue.this.transition(this, expand)
          false
        }
      }
    }

    def dequeue(): AnyRef = {
      val x = deq()
      if (x != NONE) x
      else if (READ_HEAD() < 0) REPEAT // frozen
      else NONE // empty
    }

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


object SnapQueue {

  trait SupportOps[T] {
    type Support
    def pushr(xs: Support, x: Array[T]): Support
    def popl(xs: Support): (Array[T], Support)
    def nonEmpty(xs: Support): Boolean
    def create(): Support
  }

  implicit def concTreeSupportOps[T] = new SupportOps[T] {
    type Support = Conc[Array[T]]
    def pushr(xs: Support, x: Array[T]) = ???
    def popl(xs: Support): (Array[T], Support) = ???
    def nonEmpty(xs: Support): Boolean = ???
    def create(): Support = ???
  }

  type Trans[T] = RootOrSegmentOrFrozen[T] => RootOrSegmentOrFrozen[T]

}
