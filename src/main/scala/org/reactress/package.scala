package org






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
    final def invalid = Long.MinValue
  }

  type ReactCell[T] = container.ReactCell[T]

  val ReactCell = container.ReactCell

  type ReactAggregate[T] = container.ReactAggregate[T]

  val ReactAggregate = container.ReactAggregate

  type ReactTileMap[T] = container.ReactTileMap[T]

  val ReactTileMap = container.ReactTileMap

  type ReactSet[T] = container.ReactSet[T]

  val ReactSet = container.ReactSet

  type ReactMap[K, V] = container.ReactMap[K, V]

  val ReactMap = container.ReactMap

  // TODO reactive sorted set

  // TODO reactive sequence

  type ReactContainer[T, Repr <: ReactContainer[T, Repr]] = container.ReactContainer[T, Repr]

  val ReactContainer = container.ReactContainer

  type ReactBuilder[T, Repr] = container.ReactBuilder[T, Repr]

  val ReactBuilder = container.ReactBuilder

  implicit class Tuple2Extensions[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](val tuple: (Signal[T], Signal[S])) {
    def update[M](mutable: M)(f: (T, S) => Unit) = Tuple2Extensions.update(tuple, mutable, f)
  }

  object Tuple2Extensions {
    def update[@spec(Int, Double) T, @spec(Int, Double) S, @spec(Int, Double) M](tuple: (Signal[T], Signal[S]), mutable: M, f: (T, S) => Unit) = {
      class Update extends Reactive.ProxySubscription {
        def update[R](r: Reactive[R]) = new Reactor[R] {
          def react(value: R) {
            f(tuple._1(), tuple._2())
          }
          def unreact() {
          }
        }
        val m1 = update(tuple._1)
        val m2 = update(tuple._2)
        val subscription = Reactive.CompositeSubscription(
          tuple._1.onReaction(m1),
          tuple._2.onReaction(m2)
        )
      }

      new Update
    }
  }

  implicit class Tuple3Extensions[T, S, R](val tuple: (Signal[T], Signal[S], Signal[R])) extends AnyVal {
    def update[M](mutable: M)(f: (T, S, R) => Unit) = Tuple3Extensions.update(tuple, mutable, f)
  }

  object Tuple3Extensions {
    def update[T, S, R, M](tuple: (Signal[T], Signal[S], Signal[R]), mutable: M, f: (T, S, R) => Unit) = {
      class Update extends Reactive.ProxySubscription {
        def update[R](r: Reactive[R]) = new Reactor[R] {
          def react(value: R) {
            f(tuple._1(), tuple._2(), tuple._3())
          }
          def unreact() {
          }
        }
        val m1 = update(tuple._1)
        val m2 = update(tuple._2)
        val m3 = update(tuple._3)
        val subscription = Reactive.CompositeSubscription(
          tuple._1.onReaction(m1),
          tuple._2.onReaction(m2),
          tuple._3.onReaction(m3)
        )
      }

      new Update
    }
  }

  implicit class Tuple4Extensions[P, Q, R, S](val tuple: (Signal[P], Signal[Q], Signal[R], Signal[S])) extends AnyVal {
    def update[M](mutable: M)(f: (P, Q, R, S) => Unit) = Tuple4Extensions.update(tuple, mutable, f)
  }

  object Tuple4Extensions {
    def update[P, Q, R, S, M](tuple: (Signal[P], Signal[Q], Signal[R], Signal[S]), mutable: M, f: (P, Q, R, S) => Unit) = {
      class Update extends Reactive.ProxySubscription {
        def update[R](r: Reactive[R]) = new Reactor[R] {
          def react(value: R) {
            f(tuple._1(), tuple._2(), tuple._3(), tuple._4())
          }
          def unreact() {
          }
        }
        val m1 = update(tuple._1)
        val m2 = update(tuple._2)
        val m3 = update(tuple._3)
        val m4 = update(tuple._4)
        val subscription = Reactive.CompositeSubscription(
          tuple._1.onReaction(m1),
          tuple._2.onReaction(m2),
          tuple._3.onReaction(m3),
          tuple._4.onReaction(m4)
        )
      }

      new Update
    }
  }

  @inline def mutate[M <: AnyRef](ms: Signal.Mutable[M])(f: M => Unit) {
    f(ms())
    ms.onUpdated()
  }

  trait CommuteMonoid[@spec(Int, Long, Double) T] {
    def zero: T
    def operator: (T, T) => T
  }

  trait Foreach[@spec(Int, Long, Double) T] {
    def foreach[U](f: T => U): Unit
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

}


