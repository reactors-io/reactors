package io.reactors
package container



import java.util.NoSuchElementException
import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.scalatest._
import io.reactors.test._
import scala.collection._



class RHashMatrixCheck extends Properties("RHashMatrix") with ExtendedProperties {
  val sizes = detChoose(0, 1000)

  property("contain diagonal elements") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val matrix = new RHashMatrix[Long]
      for (i <- 0 until sz) matrix(i, i) = i

      assert(matrix.size == sz)
      for (i <- 0 until sz) assert(matrix(i, i) == i, matrix(i, i))
      for (i <- 0 until sz / 2) assert(matrix.remove(i, i) == i)
      for (i <- 0 until sz / 2) assert(matrix(i, i) == Long.MinValue)
      for (i <- sz / 2 until sz) assert(matrix(i, i) == i, matrix(i, i))
      matrix.clear()
      for (i <- 0 until sz) assert(matrix(i, i) == matrix.nil)
      assert(matrix.size == 0, s"size = ${matrix.size}")
      true
    }
  }

  property("contain all elements") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val matrix = new RHashMatrix[Long]
      for (x <- 0 until sz; y <- 0 until sz) matrix(x, y) = x * y

      assert(matrix.size == sz * sz)
      for (x <- 0 until sz; y <- 0 until sz)
        assert(matrix(x, y) == x * y, matrix(x, y))
      for (x <- 0 until sz / 2; y <- 0 until sz / 2)
        assert(matrix.remove(x, y) == x * y)
      for (x <- 0 until sz / 2; y <- 0 until sz / 2)
        assert(matrix(x, y) == Long.MinValue)
      for (x <- sz / 2 until sz; y <- sz / 2 until sz)
        assert(matrix(x, y) == x * y, matrix(x, y))
      for (x <- sz / 2 until sz; y <- sz / 2 until sz)
        assert(matrix.remove(x, y) == x * y)
      matrix.clear()
      for (x <- sz / 2 until sz; y <- sz / 2 until sz)
        assert(matrix(x, y) == Long.MinValue)
      assert(matrix.size == 0, s"size = ${matrix.size}")
      true
    }
  }

}
