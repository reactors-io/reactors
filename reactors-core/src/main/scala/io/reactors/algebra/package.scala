package io.reactors






package object algebra {

  trait Monoid[@spec(Int, Long, Double) T] {
    def zero: T
    def operator: (T, T) => T
  }

  object Monoid {
    def apply[@spec(Int, Long, Double) T](z: T)(op: (T, T) => T) = new Monoid[T] {
      def zero = z
      def operator = op
    }
  }

  trait Commutoid[@spec(Int, Long, Double) T]
  extends Monoid[T]

  object Commutoid {
    def apply[@spec(Int, Long, Double) T](z: T)(op: (T, T) => T) = new Commutoid[T] {
      def zero = z
      def operator = op
    }
    def from[@spec(Int, Long, Double) T](m: Monoid[T]) = new Commutoid[T] {
      def zero = m.zero
      def operator = m.operator
    }
  }

  trait Abelian[@spec(Int, Long, Double) T]
  extends Commutoid[T] {
    def inverse: (T, T) => T
  }

  object Abelian {
    def apply[@spec(Int, Long, Double) T](z: T)(op: (T, T) => T)(inv: (T, T) => T) = new Abelian[T] {
      def zero = z
      def operator = op
      def inverse = inv
    }
  }

  object structure {
  
    implicit val intPlus = new Abelian[Int] {
      val zero = 0
      val operator = (x: Int, y: Int) => x + y
      val inverse = (x: Int, y: Int) => x - y
    }
  
    implicit val longPlus = new Abelian[Long] {
      val zero = 0L
      val operator = (x: Long, y: Long) => x + y
      val inverse = (x: Long, y: Long) => x - y
    }
  
    implicit val doublePlus = new Abelian[Double] {
      val zero = 0.0
      val operator = (x: Double, y: Double) => x + y
      val inverse = (x: Double, y: Double) => x - y
    }

    implicit val stringConcat = new Monoid[String] {
      val zero = ""
      val operator = (x: String, y: String) => x + y
    }

    implicit def seqConcat[T] = new Monoid[Seq[T]] {
      val zero = Seq()
      val operator = (x: Seq[T], y: Seq[T]) => x ++ y
    }
  
    implicit def setUnion[T] = new Monoid[collection.Set[T]] {
      val zero = collection.Set[T]()
      val operator = (x: collection.Set[T], y: collection.Set[T]) => x union y
    }

  }

  /* constants */

  val Pif: Float = math.Pi.toFloat

  /* functions */

  final def min(a: Double, b: Double) = if (a < b) a else b

  final def max(a: Double, b: Double) = if (a > b) a else b

  final def min(a: Float, b: Float) = if (a < b) a else b

  final def max(a: Float, b: Float) = if (a > b) a else b

  final def nextPow2(num: Int): Int = {
    var v = num - 1
    v |= v >> 1
    v |= v >> 2
    v |= v >> 4
    v |= v >> 8
    v |= v >> 16
    v + 1
  }

  implicit class IntOps(val v: Int) extends AnyVal {
    final def clamp(min: Int, max: Int): Int = {
      if (v < min) min
      else if (v > max) max
      else v
    }
  
    final def in_<>(a: Int, b: Int): Boolean = v > a && v < b

    final def in_|>(a: Int, b: Int): Boolean = v >= a && v < b

    final def in_<|(a: Int, b: Int): Boolean = v > a && v <= b

    final def in_||(a: Int, b: Int): Boolean = v >= a && v <= b

    final def weight(x0: Double, x1: Double): Double = 1.0 * (v - x0) / (x1 - x0)

  }

  implicit class LongOps(val v: Long) extends AnyVal {
    final def clamp(min: Long, max: Long): Long = {
      if (v < min) min
      else if (v > max) max
      else v
    }
  
    final def in_<>(a: Long, b: Long): Boolean = v > a && v < b

    final def in_|>(a: Long, b: Long): Boolean = v >= a && v < b

    final def in_<|(a: Long, b: Long): Boolean = v > a && v <= b

    final def in_||(a: Long, b: Long): Boolean = v >= a && v <= b

    final def weight(x0: Double, x1: Double): Double = 1.0 * (v - x0) / (x1 - x0)

  }

  implicit class FloatOps(val v: Float) extends AnyVal {
    final def clamp(min: Float, max: Float): Float = {
      if (v < min) min
      else if (v > max) max
      else v
    }

    final def dampen(diff: Float): Float = {
      if (v > 0) max(v - diff, 0)
      else min(v + diff, 0)
    }

    final def mix(x: Float, y: Float): Float = x * (1 - v) + y * v
  
    final def weight(x0: Double, x1: Double): Double = 1.0 * (v - x0) / (x1 - x0)

    final def in_<>(a: Float, b: Float): Boolean = v > a && v < b

    final def in_|>(a: Float, b: Float): Boolean = v >= a && v < b

    final def in_<|(a: Float, b: Float): Boolean = v > a && v <= b

    final def in_||(a: Float, b: Float): Boolean = v >= a && v <= b
    
  }

  implicit class DoubleOps(val v: Double) extends AnyVal {
    final def clamp(min: Double, max: Double): Double = {
      if (v < min) min
      else if (v > max) max
      else v
    }

    final def dampen(diff: Double): Double = {
      if (v > 0) max(v - diff, 0)
      else min(v + diff, 0)
    }

    final def mix(x: Double, y: Double): Double = x * (1 - v) + y * v
  
    final def weight(x0: Double, x1: Double): Double = 1.0 * (v - x0) / (x1 - x0)

    final def in_<>(a: Double, b: Double): Boolean = v > a && v < b

    final def in_|>(a: Double, b: Double): Boolean = v >= a && v < b

    final def in_<|(a: Double, b: Double): Boolean = v > a && v <= b

    final def in_||(a: Double, b: Double): Boolean = v >= a && v <= b
  }

}