package scala.reactive
package container



import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.testx._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import scala.collection._



class RTileMapCheck extends Properties("RTileMap") with ExtendedProperties {

  val sizes = detChoose(1, 1000)

  def rects(size: Int): Gen[(Int, Int, Int, Int)] = for {
    x <- detChoose(0, size - 1)
    y <- detChoose(0, size - 1)
    w <- detChoose(0, size - x - 1)
    h <- detChoose(0, size - y - 1)
  } yield (x, y, w, h)

  val sizeAndRects = for {
    sz <- sizes
    r <- rects(sz)
  } yield (sz, r)

  property("contain random elements") = forAllNoShrink(sizes, sizes) {
    (size, num) =>
    stackTraced {
      val table = new RTileMap[String](size, "")
      val rng = new scala.util.Random(size)
      val added = mutable.Map[(Int, Int), String]()
      for (i <- 0 until num) {
        val x = rng.nextInt(size)
        val y = rng.nextInt(size)
        if (!added.contains((x, y))) {
          table(x, y) = i.toString
          added((x, y)) = i.toString
        }
      }
      for (((x, y), elem) <- added) assert(table(x, y) == elem,
        s"at ($x, $y) = '${table(x, y)}' vs '$elem'")
      true
    }
  }

  property("contain a rectangle of elements") = forAllNoShrink(sizeAndRects) {
    case (size, (x, y, w, h)) =>
    stackTraced {
      val table = new RTileMap[String](size, "")
      val rng = new scala.util.Random(size)
      val added = mutable.Map[(Int, Int), String]()
      for (i <- x until (x + w); j <- y until (y + h)) {
        table(x, y) = i.toString
        added((x, y)) = i.toString
      }
      for (((x, y), elem) <- added)
        assert(table(x, y) == elem, s"at ($x, $y) = '${table(x, y)}' vs '$elem'")
      true
    }
  }

  property("detect modifications") = forAllNoShrink(sizes, sizes) { (size, num) =>
    stackTraced {
      import scala.reactive._
      val rng = new scala.util.Random(size)
      val tilemap = new RTileMap[String](size, null)
      val added = mutable.Map[(Int, Int), String]()
      for (i <- 0 until num) {
        val x = rng.nextInt(size)
        val y = rng.nextInt(size)
        if (!added.contains((x, y))) {
          added((x, y)) = i.toString
        }
      }
      val f = tilemap.updates foreach { case xy =>
        assert(tilemap(xy.x, xy.y) == added((xy.x, xy.y)))
      }
      for (((x, y), elem) <- added) tilemap(x, y) = elem
      true
    }
  }

}


class RTileMapSpec extends FlatSpec with ShouldMatchers {

  "A RTileMap" should "have dimension 128" in {
    val table = new RTileMap[String](128, "")
    table.dimension should equal (128)
    table(30, 30) should equal ("")
    table(90, 90) should equal ("")
  }

  it should "contain a single element" in {
    val table = new RTileMap[String](128, "")

    table(0, 0) = "ok"

    table.dimension should equal (128)
    table(0, 0) should equal ("ok")
    table(1, 1) should equal ("")
    table(56, 72) should equal ("")
  }

  it should "contain a rectangle in the middle" in {
    val table = new RTileMap[String](128, "")
    for (x <- 50 until 70; y <- 55 until 75) table(x, y) = (x, y).toString

    for (x <- 0 until 128; y <- 0 until 128) assert(table(x, y) == (
      if (x >= 50 && x < 70 && y >= 55 && y < 75) (x, y).toString
      else ""
    ))
    
  }

  it should "be big and contain a checkerboard" in {
    val big = 1024
    val table = new RTileMap[Int](1024, 0)
    for (x <- 0 until big by 2; y <- 0 until big by 2) table(x, y) = 1

    for (x <- 0 until big; y <- 0 until big) assert(table(x, y) == (
      if (x % 2 == 0 && y % 2 == 0) 1
      else 0
    ))
    for (x <- 0 until big by 2; y <- 0 until big by 2) table(x, y) = 0
    assert(table.quadRoot.isLeaf)
  }

  it should "be cleared" in {
    val big = 1024
    val table = new RTileMap[Int](1024, 0)
    for (x <- 0 until big by 2; y <- 0 until big by 2) table(x, y) = 1

    table.clear()
    assert(table(600, 150) == 0)
    assert(table.quadRoot.isLeaf)
  }

  it should "detect modifications" in {
    import scala.reactive._
    val size = 32
    val tilemap = new RTileMap[String](size, null)
    val m = tilemap.updates
    val a = m foreach { case _ =>
      assert(tilemap(0, 0) == "OK")
    }

    tilemap(0, 0) = "OK"
  }

  it should "contain the correct set of values" in {
    val size = 256
    val tilemap = new RTileMap[String](size, null)
    val observed = mutable.Set[String]()
    val insertSub = tilemap.values.inserts.foreach(observed += _)
    val removeSub = tilemap.values.removes.foreach(observed -= _)

    for (x <- 0 until 200 by 20; y <- 0 until 200 by 10) tilemap(x, y) = s"($x, $y)"
    val produced = for (x <- 0 until 200 by 20; y <- 0 until 200 by 10) yield s"($x, $y)"

    observed should equal (produced.toSet)

    tilemap(0, 0) = null

    observed should equal (produced.toSet - s"(0, 0)")
  }

}
















