package scala.reactive
package core
package concurrent



import scala.annotation.tailrec
import scala.annotation.unchecked



class SnapQueue[T](val L: Int = 128)
  (implicit val supportOps: SnapQueue.SupportOps[T])
extends SnapQueueBase[T] with Serializable {
  import SnapQueue.Trans
  import SegmentBase.{EMPTY, FROZEN, NONE, REPEAT}

  WRITE_ROOT(new Segment(new Array[AnyRef](L)))

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
    while (READ_ROOT() == fr) CAS_ROOT(fr, nr)
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
  final def freeze(r: RootOrSegmentOrFrozen[T], f: Trans[T]): Frozen = {
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
        r.READ_LEFT().asInstanceOf[Side].segment.freeze()
        r.READ_RIGHT().asInstanceOf[Side].segment.freeze()
    }
  }

  private def expand(r: RootOrSegmentOrFrozen[T]):
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

  private def transfer(r: RootOrSegmentOrFrozen[T]):
    RootOrSegmentOrFrozen[T] = {
    (r: @unchecked) match {
      case r: Root =>
        val ls = r.READ_LEFT().asInstanceOf[Side]
        val rs = r.READ_RIGHT().asInstanceOf[Side]
        if (supportOps.nonEmpty(rs.support)) {
          val (arr, sup) = supportOps.popl(rs.support)
          val seg = new Segment(arr.asInstanceOf[Array[AnyRef]])
          val nls = new Side(false, seg, sup)
          val nrs = new Side(false, rs.segment.copyShift(), supportOps.create())
          new Root(nls, nrs)
        } else {
          rs.segment.copyShift()
        }
    }
  }

  @tailrec
  final def enqueue(x: T): Unit = {
    if (!READ_ROOT().enqueue(x)) enqueue(x)
  }

  @tailrec
  final def dequeue(): T = {
    val r = READ_ROOT()
    val x = r.dequeue()
    if (x eq REPEAT) dequeue()
    else if (x ne NONE) x.asInstanceOf[T]
    else null.asInstanceOf[T]
  }

  final class Frozen(val f: Trans[T], val root: RootOrSegmentOrFrozen[T])
  extends RootOrSegmentOrFrozen[T] {
    def enqueue(x: T): Boolean = {
      helpTransition()
      false
    }
    def dequeue(): AnyRef = {
      helpTransition()
      REPEAT
    }
  }

  final class Root(l: Side, r: Side) extends RootBase[T] {
    WRITE_LEFT(l)
    WRITE_RIGHT(r)

    @tailrec
    def enqueue(x: T): Boolean = {
      val r = READ_RIGHT().asInstanceOf[Side]
      val p = r.segment.READ_LAST()
      if (r.segment.enq(p, x.asInstanceOf[AnyRef])) true
      else { // full or frozen
        if (r.isFrozen) false
        else { // full
          val seg = new Segment(r.segment.capacity)
          val array = r.segment.array.asInstanceOf[Array[T]]
          val sup = supportOps.pushr(r.support, array)
          val nr = new Side(false, seg, sup)
          CAS_RIGHT(r, nr)
          enqueue(x)
        }
      }
    }

    @tailrec
    def dequeue(): AnyRef = {
      val l = READ_LEFT().asInstanceOf[Side]
      val x = l.segment.deq()
      if (x ne NONE) x
      else { // empty or frozen
        if (l.isFrozen) REPEAT
        else { // empty
          if (supportOps.nonEmpty(l.support)) {
            val (array, sup) = supportOps.popl(l.support)
            val seg = new Segment(array.asInstanceOf[Array[AnyRef]])
            val nl = new Side(false, seg, sup)
            CAS_LEFT(l, nl)
            dequeue()
          } else {
            transition(this, transfer)
            REPEAT
          }
        }
      }
    }

    @tailrec
    def freezeLeft() {
      val l = READ_LEFT().asInstanceOf[Side]
      if (l.isFrozen) {}
      else {
        val nl = new Side(true, l.segment, l.support)
        if (!CAS_LEFT(l, nl)) freezeLeft()
      }
    }
  
    @tailrec
    def freezeRight() {
      val r = READ_RIGHT().asInstanceOf[Side]
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

  final class Segment(a: Array[AnyRef])
  extends SegmentBase[T](a) {
    def this(cap: Int) = this(new Array[AnyRef](cap))

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
          if (x eq EMPTY) {
            sys.error(s"""locate on non-frozen
              p = $p,
              x = $x,
              array(p) = ${READ_ARRAY(p)},
              frozenBy = $frozenBy vs currentThread = ${Thread.currentThread}
              $this""")
          } else if (x eq FROZEN) { WRITE_LAST(p); p }
          else locate(p + 1)
        }
      locate(READ_LAST())
    }

    /** Given a frozen, full segment, copies it into a freshly allocated one,
     *  which can be used for subsequent update operations.
     *
     *  The elements are shifted to the left of the segment as far as possible.
     *
     *  Note: undefined behavior for non-frozen segments.
     */
    def copyShift(): Segment = {
      val head = locateHead()
      val last = locateLast()
      val nseg = new Segment(capacity) // note: this.capacity == L in this call!
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

    private var frozenBy: Thread = null
    @tailrec
    def freezeLast(p: Int) {
      if (p >= 0 && p < array.length)
        if (!CAS_ARRAY(p, EMPTY, FROZEN))
          freezeLast(findLast(p))
        else {frozenBy = Thread.currentThread}
    }

    /** Only used for testing.
     */
    private[concurrent] def reinitialize() {
      WRITE_HEAD(0)
      WRITE_LAST(0)
      var i = 0
      while (i < array.length) {
        WRITE_ARRAY(i, EMPTY)
        i += 1
      }
    }

    override def toString =
      s"Segment(head: ${READ_HEAD()}; last: ${READ_LAST()}; ${array.mkString(", ")})"
  }

}


object SnapQueue {

  trait SupportOps[T] {
    type Support
    def pushr(xs: Support, x: Array[T]): Support
    def popl(xs: Support): (Array[T], Support)
    def nonEmpty(xs: Support): Boolean
    def create(): Support
    def foreach[U](xs: Support, f: T => U): Unit
  }

  implicit def concTreeSupportOps[T] = new SupportOps[T] with Serializable {
    type Support = Conc[Array[T]]
    def pushr(xs: Support, x: Array[T]) = {
      ConcRope.append(xs, new Conc.Single(x))
    }
    def popl(xs: Support): (Array[T], Support) = {
      ConcRope.unprepend(xs)
    }
    def nonEmpty(xs: Support): Boolean = {
      xs.size != 0
    }
    def create(): Support = {
      Conc.Empty
    }
    def foreach[U](xs: Support, f: T => U) = {
      ConcUtils.foreach(xs, (a: Array[T]) => {
        var i = 0
        while (i < a.length) {
          f(a(i))
          i += 1
        }
      })
    }
  }

  type Trans[T] = RootOrSegmentOrFrozen[T] => RootOrSegmentOrFrozen[T]

}
