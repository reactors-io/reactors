package scala.reactive
package core
package concurrent



import scala.annotation.tailrec
import scala.annotation.unchecked



class SnapQueue[T](val L: Int = 128)
  (implicit val supportOps: SnapQueue.SupportOps[T])
extends SnapQueueBase[T] with Serializable {
  import SnapQueue.Trans

  private def transition(r: RootOrSegmentOrFrozen[T], f: Trans[T]):
    RootOrSegmentOrFrozen[T] = {
    val fr = freeze(r, f)
    if (fr == null) null
    else {
      completeTransition(fr)
      fr.root
    }
  }

  private def completeTransition(fr: Frozen) {
    val nr = fr.f(fr.root)
    while (READ_ROOT() == fr) CAS(root, fr, nr)
  }

  private def helpTransition() {
    READ_ROOT() match {
      case fr: Frozen =>
        completeFreeze(fr.root)
        completeTransition(fr)
      case _ => // not frozen -- do nothing
    }
  }

  @tailrec
  def freeze(r: RootOrSegmentOrFrozen[T], f: Trans): Frozen = {
    val fr = new Frozen(f, r)
    if (READ_ROOT() ne r) null
    else if (CAS_ROOT(r, fr)) {
      completeFreeze(fr.root)
      fr
    } else freeze(r, f)
  }

  def completeFreeze(r: RootOrSegmentOrFrozen[T]) {
    r match {
      case s: Segment =>
        s.freeze()
      case r: Root =>
        r.freezeLeft()
        r.freezeRight()
        r.left.segment.freeze()
        r.right.segment.freeze()
    }
  }

  private def expand[T](r: RootOrSegmentOrFrozen[T]):
    RootOrSegmentOrFrozen[T] = {
    (r: @unchecked) match {
      case s: Segment =>
        val head = s.locateHead
        val last = s.locateLast
        if (last - head < s.array.length / 2) {
          s.copyShift()
        } else {
          val left = new Side(false, s.unfreeze(), supportOps.create())
          val right = new Side(false, new Segment(L), supportOps.create())
          new Root(left, right)
        }
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

    @tailrec
    private def freezeLeft() {
      val l = READ_LEFT()
      if (l.isFrozen) {}
      else {
        val nl = new Side(true, l.segment, l.support)
        if (!CAS_LEFT(l, nl)) freezeLeft()
      }
    }
  
    @tailrec
    private def freezeRight() {
      val r = READ_RIGHT()
      if (r.isFrozen) {}
      else {
        val nr = new Side(true, r.segment, r.support)
        if (!CAS_RIGHT(r, nr)) freezeRight()
      }
    }
  }

  final class Side(
    val isFrozen: Boolean,
    val segment: Segment,
    val support: supportOps.Support
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
      if (x ne NONE) x
      else if (READ_HEAD() < 0) REPEAT // frozen
      else NONE // empty
    }

    /** Given a frozen segment, locates the head position, that is, the position
     *  of the first non-dequeued element in the array.
     *
     *  Note: undefined behavior for non-frozen segments.
     */
    def locateHead(): Int = {
      val p = READ_HEAD()
      -p - 1
    }

    /** Given a frozen segment, locates the last position, that is, the position
     *  of the FROZEN entry in the array.
     *  May update the last field.
     *
     *  If the array was full when frozen, it returns the length of the array.
     *
     *  Note: undefined behavior for non-frozen segments.
     */
    def locateLast(): Int = {
      @tailrec
      def locate(p: Int): Int =
        if (p == array.length) { WRITE_LAST(p); p }
        else {
          val x = READ_ARRAY(p)
          if (x eq EMPTY) sys.error("cannot be called on non-frozen segments")
          else if (x eq FROZEN) { WRITE_LAST(p); p }
          else locate(p + 1)
        }
      locate(READ_LAST())
    }

    /** Given a frozen, full segment, copies it into a freshly allocated one,
     *  which can be used for subsequent update operations.
     *
     *  The are shifted to the left of the segment as far as possible.
     *
     *  Note: undefined behavior for non-frozen segments.
     */
    def copyShift(): Segment = {
      val head = locateHead()
      val last = locateLast()
      val nseg = new Segment(capacity)
      System.arraycopy(array, head, nseg.array, 0, last - head)
      nseg
    }

    /** Given a frozen, full segment, constructs a new segment around the same
     *  underlying array.
     *
     *  May shrink the array to avoid having empty elements at the beginning.
     *
     *  Note: undefined behavior for non-frozen segments.
     */
    def unfreeze(): Segment = {
      val head = locateHead()
      val last = locateLast()
      val nseg = new Segment(last - head)
      System.arraycopy(array, head, nseg.array, 0, last - head)
      nseg
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
      if (x eq EMPTY) p
      else if (x eq FROZEN) array.length
      else if (p + 1 == array.length) p + 1
      else findLast(p + 1)
    }

    @tailrec
    def deq(): AnyRef = {
      val p = READ_HEAD()
      if (p >= 0 && p < array.length) {
        val x = READ_ARRAY(p);
        if ((x eq EMPTY) || (x eq FROZEN)) NONE
        else if (CAS_HEAD(p, p + 1)) x
        else deq()
      } else NONE
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

  implicit def concTreeSupportOps[T] = new SupportOps[T] with Serializable {
    type Support = Conc[Array[T]]
    def pushr(xs: Support, x: Array[T]) = ???
    def popl(xs: Support): (Array[T], Support) = ???
    def nonEmpty(xs: Support): Boolean = ???
    def create(): Support = ???
  }

  type Trans[T] = RootOrSegmentOrFrozen[T] => RootOrSegmentOrFrozen[T]

}
