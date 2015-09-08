package scala.reactive
package container



import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.testx._
import org.scalatest._



class RHashValMapCheck extends Properties("RHashValMap") with ExtendedProperties {

  val sizes = detChoose(0, 1000)

  property("contain many elements") = forAllNoShrink(sizes) { size =>
    stackTraced {
      val table = new RHashValMap[Long, Int]
      for (i <- 0 until size) table(i) = i.toInt

      assert(table.size == size)
      for (i <- 0 until size) assert(table(i) == i.toInt)
      for (i <- 0 until size / 2) assert(table.remove(i) == true)
      for (i <- 0 until size / 2) assert(table.get(i) == None)
      for (i <- size / 2 until size) assert(table(i) == i.toInt)
      table.clear()
      for (i <- 0 until size) assert(table.get(i) == None)
      assert(table.size == 0)
      true
    }
  }

}


class RHashValMapSpec extends FlatSpec with Matchers {

  "A RHashValMap" should "be empty" in {
    val table = new RHashValMap[Long, Int]

    table.size should equal (0)
    table.get(0L) should equal (None)
    a [NoSuchElementException] should be thrownBy { table(0L) }
    table.remove(0L) should equal (false)
  }

  it should "contain a single element" in {
    val table = new RHashValMap[Long, Int]
    table(2L) = 2L.toInt

    table.size should equal (1)
    table.get(2L) should equal (Some(2L.toInt))
    table.apply(2L) should equal (2L.toInt)

    table.remove(2L) should equal (true)
    table.size should equal (0)
  }

  it should "contain two elements" in {
    val table = new RHashValMap[Long, Int]
    table.update(3L, 3L.toInt)
    table.update(4L, 4L.toInt)

    table.size should equal (2)
    table.get(3L) should equal (Some(3L.toInt))
    table.apply(4L) should equal (4L.toInt)
    table.get(5L) should equal (None)
  }

  it should "contain several elements" in {
    val table = new RHashValMap[Int, Int]
    table.update(0, 1)
    table.update(1, 2)
    table.update(2, 3)
    table.update(3, 4)

    table.size should equal (4)
    table(0) should equal (1)
    table(1) should equal (2)
    table(2) should equal (3)
    table(3) should equal (4)

    table.remove(1) should equal (true)
    table.remove(2) should equal (true)
    table(0) should equal (1)
    table(3) should equal (4)
  }

}



