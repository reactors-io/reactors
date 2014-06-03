package scala.reactive






trait Order[@specialized(Int, Long, Double) T] {

  def compare(x: T, y: T): Int

  def lteq(x: T, y: T) = compare(x, y) <= 0

  def gteq(x: T, y: T) = compare(x, y) >= 0

  def lt(x: T, y: T) = compare(x, y) < 0

  def gt(x: T, y: T) = compare(x, y) > 0

  def equiv(x: T, y: T) = compare(x, y) == 0

}


object Order {

  implicit object IntOrder extends Order[Int] {
    def compare(x: Int, y: Int) =
      if (x < y) -1
      else if (x == y) 0
      else 1
  }

  implicit object LongOrder extends Order[Long] {
    def compare(x: Long, y: Long) =
      if (x < y) -1
      else if (x == y) 0
      else 1
  }

  implicit object DoubleOrder extends Order[Double] {
    def compare(x: Double, y: Double) =
      if (x < y) -1
      else if (x == y) 0
      else 1
  }

  implicit def fromOrdering[T <: AnyRef: Ordering] = new Order[T] {
    val ordering = implicitly[Ordering[T]]
    def compare(x: T, y: T) = ordering.compare(x, y)
  }

}
