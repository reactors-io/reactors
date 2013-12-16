package org.reactress






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
  
    implicit def setUnion[T] = new Monoid[Set[T]] {
      val zero = Set[T]()
      val operator = (x: Set[T], y: Set[T]) => x union y
    }

  }

}