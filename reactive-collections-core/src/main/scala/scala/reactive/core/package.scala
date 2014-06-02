package scala.reactive



import scala.reflect.ClassTag



package object core {

  def invalid(msg: String) = throw new IllegalStateException(msg)

  def unsupported(msg: String) = throw new UnsupportedOperationException(msg)

  implicit class ConcApi[T](val self: Conc[T]) extends AnyVal {
    def apply(i: Int) = {
      require(i >= 0 && i < self.size)
      ConcOps.apply(self, i)
    }
    def foreach[U](f: T => U) = ConcOps.foreach(self, f)
    def <>(that: Conc[T]) = ConcOps.concatTop(self.normalized, that.normalized)
  }

  implicit class ConcModificationApi[@specialized(Byte, Char, Int, Long, Float, Double) T: ClassTag](val self: Conc[T]) {
    def update(i: Int, y: T) = {
      require(i >= 0 && i < self.size)
      ConcOps.update(self, i, y)
    }
    def insert(i: Int, y: T) = {
      require(i >= 0 && i <= self.size)
      ConcOps.insert(self, i, y)
    }
    def rappend(y: T) = ConcOps.appendTop(self, new Conc.Single(y))
  }

  implicit class ConqueueApi[T: ClassTag](@specialized(Byte, Char, Int, Long, Float, Double) val self: Conqueue[T]) {
    import Conc._
    import Conqueue._
    def head: T = (ConcOps.head(self): @unchecked) match {
      case s: Single[T] => s.x
      case c: Chunk[T] => c.array(0)
      case null => unsupported("empty")
    }
    def last: T = (ConcOps.last(self): @unchecked) match {
      case s: Single[T] => s.x
      case c: Chunk[T] => c.array(c.size - 1)
      case null => unsupported("empty")
    }
    def tail: Conqueue[T] = (ConcOps.head(self): @unchecked) match {
      case s: Single[T] =>
        ConcOps.popHeadTop(self)
      case c: Chunk[T] =>
        val popped = ConcOps.popHeadTop(self)
        if (c.size == 1) popped
        else {
          val nhead = new Chunk(ConcOps.removedArray(c.array, 0, 0, c.size), c.size - 1, c.k)
          ConcOps.pushHeadTop(popped, nhead)
        }
      case null =>
        unsupported("empty")
    }
    def init: Conqueue[T] = (ConcOps.last(self): @unchecked) match {
      case s: Single[T] =>
        ConcOps.popLastTop(self)
      case c: Chunk[T] =>
        val popped = ConcOps.popLastTop(self)
        if (c.size == 1) popped
        else {
          val nlast = new Chunk(ConcOps.removedArray(c.array, 0, c.size - 1, c.size), c.size - 1, c.k)
          ConcOps.pushLastTop(popped, nlast)
        }
      case null =>
        unsupported("empty")
    }
    def :+(y: T) = (ConcOps.last(self): @unchecked) match {
      case s: Single[T] =>
        ConcOps.pushLastTop(self, new Single(y))
      case c: Chunk[T] if c.size == c.k =>
        val na = new Array[T](1)
        na(1) = y
        val nc = new Chunk(na, 1, c.k)
        ConcOps.pushLastTop(self, nc)
      case c: Chunk[T] =>
        val popped = ConcOps.popLastTop(self)
        val nlast = new Chunk(ConcOps.insertedArray(c.array, 0, c.size, y, c.size), c.size + 1, c.k)
        ConcOps.pushLastTop(popped, nlast)
      case null =>
        Tip(One(new Single(y)))
    }
    def +:(y: T) = (ConcOps.head(self): @unchecked) match {
      case s: Single[T] =>
        ConcOps.pushHeadTop(self, new Single(y))
      case c: Chunk[T] if c.size == c.k =>
        val na = new Array[T](1)
        na(1) = y
        val nc = new Chunk(na, 1, c.k)
        ConcOps.pushHeadTop(self, nc)
      case c: Chunk[T] =>
        val popped = ConcOps.popHeadTop(self)
        val nlast = new Chunk(ConcOps.insertedArray(c.array, 0, 0, y, c.size), c.size + 1, c.k)
        ConcOps.pushHeadTop(popped, nlast)
      case null =>
        Tip(One(new Single(y)))
    }
    def isEmpty = ConcOps.isEmptyConqueue(self)
    def nonEmpty = !isEmpty
    def <|>(that: Conqueue[T]) = ConcOps.concatConqueueTop(self, that)
  }

}










