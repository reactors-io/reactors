package scala.reactive
package core
package concurrent



import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec
import scala.annotation.unchecked
import scala.concurrent._
import scala.concurrent.duration._



/** Concurrent queue with constant-time snapshots.
 */
class SnapQueue[T] private[concurrent] (
  val L: Int,
  val mustInitialize: Boolean,
  val leaky: Boolean
)(
  implicit val supportOps: SnapQueue.SupportOps[T]
) extends SnapQueueBase[T] with Serializable {
  import SnapQueue.Trans
  import SegmentBase.{EMPTY, FROZEN, NONE, REPEAT, REMOVED}

  val stamp = SnapQueue.stamp(this)

  /** Creates a new SnapQueue.
   *
   *  @param L       length of the SnapQueue segments, usually between 32 and 256
   *  @param leaky   whether or not the SnapQueue can leak (`false` by default) -- gives
   *                 a small performance boost when `true`, with the disadvantage that
   *                 up to `L` previously dequeued elements are garbage collectable
   *                 (useful for applications where elements are small)
   */
  def this(L: Int, leaky: Boolean)(implicit ops: SnapQueue.SupportOps[T]) =
    this(L, true, leaky)

  /** Creates a new SnapQueue.
   *
   *  @param L       length of the SnapQueue segments, usually between 32 and 256
   */
  def this(L: Int)(implicit ops: SnapQueue.SupportOps[T]) = this(L, false)

  /** Creates a new SnapQueue, with the segment length set to 128.
   */
  def this()(implicit ops: SnapQueue.SupportOps[T]) = this(128)

  if (mustInitialize) WRITE_ROOT(new Segment(new Array[AnyRef](L)))

  private def transition(r: RootOrSegmentOrFrozen[T], f: Trans[T]):
    RootOrSegmentOrFrozen[T] = {
    r match {
      case fr: Frozen =>
        completeFreeze(fr.root)
        completeTransition(fr)
        null
      case r =>
        val fr = freeze(r, f)
        if (fr == null) null
        else {
          completeTransition(fr)
          fr.root
        }
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

  private def expand(r: RootOrSegmentOrFrozen[T]): RootOrSegmentOrFrozen[T] = {
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

  private def transfer(r: RootOrSegmentOrFrozen[T]): RootOrSegmentOrFrozen[T] = {
    (r: @unchecked) match {
      case r: Root =>
        val ls = r.READ_LEFT().asInstanceOf[Side]
        val rs = r.READ_RIGHT().asInstanceOf[Side]
        if (supportOps.nonEmpty(rs.support)) {
          val (arr, sup) = supportOps.popl(rs.support, !leaky)
          val seg = new Segment(arr.asInstanceOf[Array[AnyRef]])
          val nls = new Side(false, seg, sup)
          val nrs = new Side(false, rs.segment.copyShift(), supportOps.create())
          new Root(nls, nrs)
        } else {
          rs.segment.copyShift()
        }
    }
  }

  private def id(r: RootOrSegmentOrFrozen[T]): RootOrSegmentOrFrozen[T] = {
    (r: @unchecked) match {
      case s: Segment =>
        s.copyShift()
      case r: Root =>
        val lside = r.left.asInstanceOf[Side]
        val rside = r.right.asInstanceOf[Side]
        new Root(
          new Side(false, lside.segment.unfreeze(), lside.support),
          new Side(false, rside.segment.copyShift(), rside.support))
    }
  }

  @tailrec
  private def snapshotInternalFrozen(): RootOrSegmentOrFrozen[T] = {
    val r = READ_ROOT()
    val nr = transition(r, id)
    if (nr == null) {
      helpTransition()
      snapshotInternalFrozen()
    } else nr
  }

  final def snapshot(): SnapQueue[T] = {
    val nr = snapshotInternalFrozen()
    val ur = id(nr)
    val snapq = new SnapQueue(L, false)
    snapq.WRITE_ROOT(ur)
    snapq
  }

  @tailrec
  private def concatInternalPromise(that: SnapQueue[T]):
    Promise[(RootOrSegmentOrFrozen[T], RootOrSegmentOrFrozen[T])] = {
    val p = Promise[(RootOrSegmentOrFrozen[T], RootOrSegmentOrFrozen[T])]()

    // capture root tuple
    import scala.math.Ordering.Implicits._
    if (this.stamp < that.stamp) {
      val r = this.READ_ROOT()
      val nr = transition(r, rthis => {
        if (!p.isCompleted) {
          val rthat = that.snapshotInternalFrozen()
          p.trySuccess((rthis, rthat))
        }
        id(rthis)
      })
      if (nr != null) p
      else this.concatInternalPromise(that)
    } else {
      val r = that.READ_ROOT()
      val nr = transition(r, rthat => {
        if (!p.isCompleted) {
          val rthis = this.snapshotInternalFrozen()
          p.trySuccess((rthis, rthat))
        }
        id(rthat)
      })
      if (nr != null) p
      else this.concatInternalPromise(that)
    }
  }

  private def mergeSegments(xs: Segment, ys: Segment): Segment = {
    new Segment(xs.unfreeze().array ++ ys.unfreeze().array)
  }

  private def concatInternal(that: SnapQueue[T]): RootOrSegmentOrFrozen[T] = {
    val p = this.concatInternalPromise(that)
    p.future.value.get.get match {
      case (rthis: Root, rthat: Root) =>
        val rthisl = rthis.READ_LEFT().asInstanceOf[Side]
        val rthisr = rthis.READ_RIGHT().asInstanceOf[Side]
        val rthatl = rthat.READ_LEFT().asInstanceOf[Side]
        val rthatr = rthat.READ_RIGHT().asInstanceOf[Side]
        val rthisrnsup = supportOps.pushr(
          supportOps.pushr(rthisr.support, rthisr.segment.unfreeze().arrayT),
          rthatl.segment.unfreeze().arrayT)
        val nlsup = supportOps.concat(
          supportOps.concat(rthisl.support, rthisrnsup),
          rthatl.support)
        new Root(
          new Side(false, rthisl.segment.unfreeze(), nlsup),
          new Side(false, rthatr.segment.copyShift(), rthatr.support))
      case (rthis: Segment, rthat: Root) =>
        val rthatl = rthat.READ_LEFT().asInstanceOf[Side]
        val rthatr = rthat.READ_RIGHT().asInstanceOf[Side]
        val nlsup = supportOps.concat(
          supportOps.pushr(supportOps.create(), rthatl.segment.unfreeze().arrayT),
          rthatl.support)
        new Root(
          new Side(false, rthis.unfreeze(), nlsup),
          new Side(false, rthatr.segment.copyShift(), rthatr.support))
      case (rthis: Root, rthat: Segment) =>
        val rthisl = rthis.READ_LEFT().asInstanceOf[Side]
        val rthisr = rthis.READ_RIGHT().asInstanceOf[Side]
        val nrsup = supportOps.pushr(rthisr.support, rthisr.segment.unfreeze().arrayT)
        new Root(
          new Side(false, rthisl.segment.unfreeze(), rthisl.support),
          new Side(false, rthat.copyShift(), nrsup))
      case (rthis: Segment, rthat: Segment) =>
        if (rthis.locateSize() + rthat.locateSize() <= L / 2) {
          mergeSegments(rthis, rthat)
        } else {
          new Root(
            new Side(false, rthis.unfreeze(), supportOps.create()),
            new Side(false, rthat.copyShift(), supportOps.create()))
        }
    }
  }

  final def concat(that: SnapQueue[T]): SnapQueue[T] = {
    val nr = this.concatInternal(that)
    val snapq = new SnapQueue(L, false)
    snapq.WRITE_ROOT(nr)
    snapq
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
            val (array, sup) = supportOps.popl(l.support, !leaky)
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

    def arrayT: Array[T] = array.asInstanceOf[Array[T]]

    /** Given a frozen segment, locates the head position, that is, the position
     *  of the first non-dequeued element in the array.
     *
     *  Note: throws an exception for non-frozen segments.
     */
    def locateHead(): Int = {
      val p = READ_HEAD()
      assert(p < 0)
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

    /** Using the locateHead and locateLast methods, computes the frozen segment size.
     */
    def locateSize(): Int = locateLast() - locateHead()

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
      val nseg = new Segment(capacity)
      System.arraycopy(array, head, nseg.array, 0, last - head)
      nseg
    }

    /** Given a frozen, full segment, constructs a new segment with a new array.
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
        val x = READ_ARRAY(p)
        if ((x eq EMPTY) || (x eq FROZEN)) NONE
        else if (CAS_HEAD(p, p + 1)) {
          if (!leaky) WRITE_ARRAY(p, REMOVED)
          x
        } else deq()
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
    def concat(xs: Support, ys: Support): Support
    def create(): Support
    def foreach[U](xs: Support, f: T => U): Unit

    def popl(xs: Support, copyArray: Boolean): (Array[T], Support) = {
      if (copyArray) poplAndCopy(xs)
      else popl(xs)
    }
    private def poplAndCopy(xs: Support): (Array[T], Support) = {
      val (a, nxs) = popl(xs)
      val na = new Array[AnyRef](a.length)
      System.arraycopy(a, 0, na, 0, a.length)
      (na.asInstanceOf[Array[T]], nxs)
    }
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
    def concat(xs: Support, ys: Support): Support = {
      ConcUtils.concat(xs, ys)
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

  object AtomicLongStamper {
    val counter = new AtomicLong(0L)
    def stamp[T](xs: SnapQueue[T]): Long = counter.getAndIncrement()
  }

  private val machineId: String = scala.util.Random.nextString(64)

  def stamp[T](q: SnapQueue[T]) = (AtomicLongStamper.stamp(q), machineId)

  type Trans[T] = RootOrSegmentOrFrozen[T] => RootOrSegmentOrFrozen[T]

}
