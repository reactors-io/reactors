package io.reactors
package common



import io.reactors.algebra._
import io.reactors.test._
import org.scalacheck._
import org.scalacheck.Gen.choose
import org.scalacheck.Prop.forAllNoShrink
import org.scalatest.FunSuite
import org.scalatest.Matchers
import scala.collection._
import scala.util.Random



class QuadMatrixCheck extends Properties("QuadMatrix") with ExtendedProperties {
  val sizes = detChoose(0, 32)

  property("update and apply rectangle") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val quad = new QuadMatrix[Int]

      for (x <- 0 until sz; y <- 0 until sz) {
        quad(x, y) = x * y
        assert(quad(x, y) == x * y)
      }

      for (x <- 0 until sz; y <- 0 until sz) {
        assert(quad(x, y) == x * y)
      }

      true
    }
  }

  property("update and apply in random order") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val quad = new QuadMatrix[Int]
      val rand = new Random(sz)
      val xs = rand.shuffle((0 until sz).to[mutable.Buffer])
      val ys = rand.shuffle((0 until sz).to[mutable.Buffer])

      for (x0 <- 0 until sz; y0 <- 0 until sz) {
        val x = xs(x0)
        val y = ys(y0)
        quad(x, y) = x * y
        assert(quad(x, y) == x * y)
      }

      for (x0 <- 0 until sz; y0 <- 0 until sz) {
        val x = xs(x0)
        val y = ys(y0)
        assert(quad(x, y) == x * y)
      }

      true
    }
  }

  property("traverse its random dense elements") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val quad = new QuadMatrix[Int]
      val rand = new Random(sz - 1)
      val xs = rand.shuffle((0 until (4 * sz)).to[mutable.Buffer]).take(sz / 2)
      val ys = rand.shuffle((0 until (4 * sz)).to[mutable.Buffer]).take(sz / 2)

      for (x <- xs; y <- ys) quad(x, y) = x * y

      val seen = mutable.Set[XY]()
      for (xy <- quad) seen += xy

      for (x <- xs; y <- ys) assert(seen.contains(XY(x, y)))
      true
    }
  }

  property("traverse its random sparse elements") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val quad = new QuadMatrix[Int]
      val rand = new Random(sz + 1)
      val xs = rand.shuffle((0 until sz).to[mutable.Buffer])
      val ys = rand.shuffle((0 until sz).to[mutable.Buffer])

      for ((x, y) <- xs.zip(ys)) quad(x, y) = x * y

      val seen = mutable.Set[XY]()
      for (xy <- quad) seen += xy

      seen.map(xy => (xy.x, xy.y)) == (xs.zip(ys)).toSet
    }
  }

  property("remove elements in a rectangle") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val quad = new QuadMatrix[Int]
      for (x <- 0 until sz; y <- 0 until sz) {
        quad(x, y) = x * y
      }
      for (x <- 0 until sz; y <- 0 until sz) {
        assert(quad(x, y) == x * y)
        quad.remove(x, y)
        assert(quad(x, y) == quad.nil)
      }
      true
    }
  }

  property("remove random elements") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val quad = new QuadMatrix[Int]
      val rand = new Random(sz + 2)
      val xs = rand.shuffle((0 until sz).to[mutable.Buffer])
      val ys = rand.shuffle((0 until sz).to[mutable.Buffer])

      for ((x, y) <- xs.zip(ys)) {
        quad(x, y) = x * y
      }
      for ((x, y) <- xs.zip(ys)) {
        assert(quad(x, y) == x * y)
        quad.remove(x, y)
        assert(quad(x, y) == quad.nil)
      }
      for ((x, y) <- xs.zip(ys)) {
        assert(quad(x, y) == quad.nil)
      }
      true
    }
  }

  property("compress the quad tree after removing") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val quad = new QuadMatrix[(Int, Int)]
      for (x <- 0 until sz; y <- 0 until sz) quad(x, y) = (x, y)

      for (x <- 0 until sz; y <- 0 until (sz - 1)) quad.remove(x, y)
      for (x <- 0 until (sz - 1)) quad.remove(x, sz - 1)

      for (x <- 0 until sz; y <- 0 until sz) {
        if (x == sz - 1 && y == sz - 1) assert(quad(x, y) == (x, y))
        else assert(quad(x, y) == quad.nil)
      }
      sz == 0 || quad.isTopLevelLeafAt(sz - 1, sz - 1)
    }
  }

}
