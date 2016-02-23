package scala.reactive
package container



import java.util.NoSuchElementException
import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.scalatest._
import io.reactors.test._
import scala.collection._



class RHashMapCheck extends Properties("RHashMap") with ExtendedProperties {
  val sizes = detChoose(0, 1000)

  property("contain elements") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val table = new RHashMap[Long, String]
      for (i <- 0 until sz) table(i) = i.toString

      assert(table.size == sz)
      for (i <- 0 until sz) assert(table(i) == i.toString, table(i))
      for (i <- 0 until sz / 2) assert(table.remove(i) == true)
      for (i <- 0 until sz / 2) assert(table.get(i) == None)
      for (i <- sz / 2 until sz) assert(table(i) == i.toString, table(i))
      table.clear()
      for (i <- 0 until sz) assert(table.get(i) == None)
      assert(table.size == 0, s"size = ${table.size}")
      true
    }
  }

  property("subscribe to many keys") = forAllNoShrink(sizes, sizes) { (sz, many) =>
    stackTraced {
      val table = new RHashMap[Int, String]
      for (i <- 0 until many) table(i) = i.toString
      val signals = for (i <- 0 until many) yield table.react(i)
      for (i <- 0 until sz) table(i) = s"value$i"
      for (i <- 0 until many)
        assert(i >= sz || signals(i)() == s"value$i", signals(i)())
      val moresignals = for (i <- many until sz) yield table.react(i)
      for (i <- many until sz) assert(moresignals(i - many)() == s"value$i")
      true
    }
  }

  property("subscribe to non-existing") = forAllNoShrink(sizes, sizes) { (sz, many) =>
    stackTraced {
      val table = new RHashMap[Int, String]
      val signsOfLife = Array.fill(many)(false)
      val subs = for (i <- 0 until many) yield {
        val values = table.react(i)
        values foreach { _ =>
          signsOfLife(i) = true
        }
      }

      for (i <- 0 until sz) table(i) = "foobar"
      for (i <- 0 until many) assert(i >= sz || signsOfLife(i) == true)
      true
    }
  }

  property("accurately GC stale key subscriptions") = forAllNoShrink(sizes, sizes) {
    (size, many) =>
    stackTraced {
      val table = new RHashMap[Int, String]
      val signsOfLife = Array.fill(many)(false)
      for (i <- 0 until many) yield table.react(i)

      sys.runtime.gc()

      for (i <- 0 until size) table(i) = "foobar"
      for (i <- 0 until many) assert(signsOfLife(i) == false)
      true
    }
  }

  property("contain the correct set of keys") = forAllNoShrink(sizes) { size =>
    stackTraced {
      val table = new RHashMap[Int, String]
      val observed = mutable.Set[Int]()
      val keys = table.keys
      val insertSub = keys.inserts.foreach(observed += _)
      for (i <- 0 until size) table(i) = i.toString

      observed == ((0 until size).toSet)
    }
  }

  property("contain the correct set of values") = forAllNoShrink(sizes) { size =>
    stackTraced {
      val table = new RHashMap[Int, String]
      val observed = mutable.Set[String]()
      val values = table.values
      val insertSub = values.inserts.foreach(observed += _.toString)
      for (i <- 0 until size) table(i) = i.toString

      observed == ((0 until size).map(_.toString).toSet)
    }
  }

  property("have a valid entries container") = forAllNoShrink(sizes) { size =>
    stackTraced {
      val table = new RHashMap[Int, String]
      val threeDigits = table.entries.collect2({
        case s if s.length > 2 => s
      }).react.to[RHashMap[Int, String]]
      for (i <- 0 until size) table(i) = i.toString

      val check = mutable.Buffer[Int]()
      threeDigits foreach {
        case (k, v) => check += k
      }

      check.sorted == (100 until size)
    }
  }

  property("have entries inverted and mapped") = forAllNoShrink(sizes) {
    (size) =>
    import scala.reactive.calc.RVFun
    stackTraced {
      val big = size * 4
      val table = new RHashMap[Int, math.BigInt]
      val bigIntToInt = new RVFun[math.BigInt, Int] { def apply(x: BigInt) = x.toInt }
      val lessThanBig = table.entries.collect2({
        case b if b < big => b
      }).rvmap2(bigIntToInt).swap.react.to[RHashValMap[Int, Int]]
    
      for (i <- 0 until size) table(-i) = math.BigInt(i)
      table(-big) = math.BigInt(big)

      val check = mutable.Buffer[(Int, Int)]()
      lessThanBig foreach {
        case (k, v) => check += ((k, v))
      }

      check.sorted == (0 until size).map(i => (i, -i))
    }
  }

}


class RHashMapSpec extends FlatSpec with Matchers {

  "A RHashMap" should "be empty" in {
    val table = new RHashMap[Long, String]

    table.size should equal (0)
    table.get(0L) should equal (None)
    a [NoSuchElementException] should be thrownBy { table(0L) }
    table.remove(0L) should equal (false)
  }

  it should "contain a single element" in {
    val table = new RHashMap[Long, String]
    table(2L) = 2L.toString

    table.size should equal (1)
    table.get(2L) should equal (Some(2L.toString))
    table.apply(2L) should equal (2L.toString)

    table.remove(2L) should equal (true)
    table.size should equal (0)
  }

  it should "contain two elements" in {
    val table = new RHashMap[Long, String]
    table.update(3L, 3L.toString)
    table.update(4L, 4L.toString)

    table.size should equal (2)
    table.get(3L) should equal (Some(3L.toString))
    table.apply(4L) should equal (4L.toString)
    table.get(5L) should equal (None)
  }

  it should "contain several elements" in {
    containSeveralElements()
  }

  def containSeveralElements() {
    val table = new RHashMap[String, String]
    table.update("a", "1")
    table.update("b", "2")
    table.update("c", "3")
    table.update("d", "4")

    table.size should equal (4)
    table("a") should equal ("1")
    table("b") should equal ("2")
    table("c") should equal ("3")
    table("d") should equal ("4")

    table.remove("b") should equal (true)
    table.remove("c") should equal (true)
    table("a") should equal ("1")
    table("d") should equal ("4")
  }

  it should "subscribe to a specific key" in {
    val many = 512
    val table = new RHashMap[Int, String]
    for (i <- 0 until many) table(i) = i.toString
    val specificKey = table.react(128)

    table(128) = "new value"
    specificKey() should equal ("new value")
  }

  it should "subscribe to optional values of a key" in {
    val many = 512
    val table = new RHashMap[Int, String]
    for (i <- 0 until many) table(i) = i.toString
    table.remove(128)
    val specificKey = table.react.get(128)

    specificKey() should equal (None)
    table(128) = "new value"
    specificKey() should equal (Some("new value"))
  }

}
