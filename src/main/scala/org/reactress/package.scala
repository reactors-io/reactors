package org



import scala.annotation.implicitNotFound
import scala.reflect.ClassTag



package object reactress {

  type spec = specialized

  type XY = Long

  implicit class XYExtensions(val v: XY) extends AnyVal {
    final def x = XY.xOf(v)
    final def y = XY.yOf(v)
    final def +(that: XY) = XY(x + that.x, y + that.y)
    final def -(that: XY) = XY(x - that.x, y - that.y)
    final def *(v: Int) = XY(x * v, y * v)

    override def toString = "XY(%d, %d)".format(x, y)
  }

  object XY {
    final def xOf(v: Long) = (v & 0xffffffff).toInt
    final def yOf(v: Long) = (v >>> 32).toInt
    final def apply(x: Int, y: Int): XY = value(x, y)
    final def value(x: Int, y: Int): Long = (y.toLong << 32) | (x.toLong & 0xffffffffL)
    final def invalid = (Int.MinValue.toLong << 32) | ((Int.MinValue >>> 1).toLong << 1)
  }

  type SubscriptionSet = container.SubscriptionSet

  val SubscriptionSet = container.SubscriptionSet

  type ReactCell[T] = container.ReactCell[T]

  val ReactCell = container.ReactCell

  type ReactRecord = container.ReactRecord

  val ReactRecord = container.ReactRecord

  type ReactContainer[T] = container.ReactContainer[T]

  val ReactContainer = container.ReactContainer

  type ReactBuilder[T, Repr] = container.ReactBuilder[T, Repr]

  val ReactBuilder = container.ReactBuilder

  type ReactSet[T] = container.ReactSet[T]

  val ReactSet = container.ReactSet

  type ReactTable[K, V] = container.ReactTable[K, V]

  val ReactTable = container.ReactTable

  type ReactMap[K, V >: Null <: AnyRef] = container.ReactMap[K, V]

  val ReactMap = container.ReactMap

  type ReactCatamorph[T, S] = container.ReactCatamorph[T, S]

  val ReactCatamorph = container.ReactCatamorph

  type MonoidCatamorph[T, S] = container.MonoidCatamorph[T, S]

  val MonoidCatamorph = container.MonoidCatamorph

  type CommuteCatamorph[T, S] = container.CommuteCatamorph[T, S]

  val CommuteCatamorph = container.CommuteCatamorph

  type AbelianCatamorph[T, S] = container.AbelianCatamorph[T, S]

  val AbelianCatamorph = container.AbelianCatamorph

  type ReactTileMap[T] = container.ReactTileMap[T]

  val ReactTileMap = container.ReactTileMap

  type CataSignaloid[T] = container.CataSignaloid[T]

  val CataSignaloid = container.CataSignaloid

  /* algebra */

  type Monoid[T] = algebra.Monoid[T]

  val Monoid = algebra.Monoid

  type Commutoid[T] = algebra.Commutoid[T]

  val Commutoid = algebra.Commutoid

  type Abelian[T] = algebra.Abelian[T]

  val Abelian = algebra.Abelian

  trait Foreach[@spec(Int, Long, Double) T] {
    def foreach[@spec(Int, Long, Double) U](f: T => U): Unit
  }

  private[reactress] def nextPow2(num: Int): Int = {
    var v = num - 1
    v |= v >> 1
    v |= v >> 2
    v |= v >> 4
    v |= v >> 8
    v |= v >> 16
    v + 1
  }

  @implicitNotFound("This operation can buffer events, and may consume an arbitrary amount of memory. " +
    "Import the value `Permission.canBuffer` to allow buffering operations.")
  sealed trait CanBeBuffered

  object Permission {
    implicit def canBuffer = new CanBeBuffered {}
  }

  abstract class Arrayable[@spec(Int, Long) T] {
    val classTag: ClassTag[T]
    val nil: T
    def newArray(sz: Int): Array[T]
    def newRawArray(sz: Int): Array[T]
  }

  object Arrayable {
  
    implicit def ref[T >: Null <: AnyRef: ClassTag]: Arrayable[T] = new Arrayable[T] {
      val classTag = implicitly[ClassTag[T]]
      val nil = null
      def newArray(sz: Int) = new Array[T](sz)
      def newRawArray(sz: Int) = newArray(sz)
    }
  
    implicit val long: Arrayable[Long] = new Arrayable[Long] {
      val classTag = implicitly[ClassTag[Long]]
      val nil = Long.MinValue
      def newArray(sz: Int) = Array.fill[Long](sz)(nil)
      def newRawArray(sz: Int) = new Array[Long](sz)
    }
  
    implicit val double: Arrayable[Double] = new Arrayable[Double] {
      val classTag = implicitly[ClassTag[Double]]
      val nil = Double.NaN
      def newArray(sz: Int) = Array.fill[Double](sz)(nil)
      def newRawArray(sz: Int) = new Array[Double](sz)
    }
  
    implicit val int: Arrayable[Int] = new Arrayable[Int] {
      val classTag = implicitly[ClassTag[Int]]
      val nil = Int.MinValue
      def newArray(sz: Int) = Array.fill[Int](sz)(nil)
      def newRawArray(sz: Int) = new Array[Int](sz)
    }

    val nonZeroInt: Arrayable[Int] = new Arrayable[Int] {
      val classTag = implicitly[ClassTag[Int]]
      val nil = 0
      def newArray(sz: Int) = newRawArray(sz)
      def newRawArray(sz: Int) = new Array[Int](sz)
    }
  
  }

  /* extensions on tuples */

  class Tuple2Extensions[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](val tuple: (Signal[T], Signal[S])) {
    def mutate[M <: ReactMutable](mutable: M)(f: (T, S) => Unit) = {
      val s = new Tuple2Extensions.Mutate(tuple, mutable, f)
      s.subscription = Reactive.CompositeSubscription(
        tuple._1.onReaction(s.m1),
        tuple._2.onReaction(s.m2)
      )
      s
    }
  }

  implicit def tuple2ext[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](tuple: (Signal[T], Signal[S])) = new Tuple2Extensions[T, S](tuple)

  object Tuple2Extensions {
    class Mutate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, M <: ReactMutable](tuple: (Signal[T], Signal[S]), mutable: M, f: (T, S) => Unit)
    extends Reactive.ProxySubscription {
      val m1 = new Reactor[T] {
        def react(value: T) {
          f(tuple._1(), tuple._2())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      val m2 = new Reactor[S] {
        def react(value: S) {
          f(tuple._1(), tuple._2())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      var subscription = Reactive.Subscription.empty
    }
  }

  class Tuple3Extensions[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S,  @spec(Int, Long, Double) U](val tuple: (Signal[T], Signal[S], Signal[U])) {
    def mutate[M <: ReactMutable](mutable: M)(f: (T, S, U) => Unit) = {
      val s = new Tuple3Extensions.Mutate(tuple, mutable, f)
      s.subscription = Reactive.CompositeSubscription(
        tuple._1.onReaction(s.m1),
        tuple._2.onReaction(s.m2),
        tuple._3.onReaction(s.m3)
      )
      s
    }
  }

  implicit def tuple3ext[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S,  @spec(Int, Long, Double) U](tuple: (Signal[T], Signal[S], Signal[U])) = new Tuple3Extensions[T, S, U](tuple)

  object Tuple3Extensions {
    class Mutate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, @spec(Int, Long, Double) U, M <: ReactMutable](tuple: (Signal[T], Signal[S], Signal[U]), mutable: M, f: (T, S, U) => Unit)
    extends Reactive.ProxySubscription {
      val m1 = new Reactor[T] {
        def react(value: T) {
          f(tuple._1(), tuple._2(), tuple._3())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      val m2 = new Reactor[S] {
        def react(value: S) {
          f(tuple._1(), tuple._2(), tuple._3())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      val m3 = new Reactor[U] {
        def react(value: U) {
          f(tuple._1(), tuple._2(), tuple._3())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      var subscription = Reactive.Subscription.empty
    }
  }

}


