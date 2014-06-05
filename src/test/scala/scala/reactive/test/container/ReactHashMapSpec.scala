package scala.reactive
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import java.util.NoSuchElementException
import scala.collection._



class ReactHashMapSpec extends FlatSpec with ShouldMatchers {

  "A ReactHashMap" should "be empty" in {
    val table = new ReactHashMap[Long, String]

    table.size should equal (0)
    table.get(0L) should equal (None)
    evaluating { table(0L) } should produce [NoSuchElementException]
    table.remove(0L) should equal (false)
  }

  it should "contain a single element" in {
    val table = new ReactHashMap[Long, String]
    table(2L) = 2L.toString

    table.size should equal (1)
    table.get(2L) should equal (Some(2L.toString))
    table.apply(2L) should equal (2L.toString)

    table.remove(2L) should equal (true)
    table.size should equal (0)
  }

  it should "contain two elements" in {
    val table = new ReactHashMap[Long, String]
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
    val table = new ReactHashMap[String, String]
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

  it should "contain many elements" in {
    containManyElements()
  }

  def containManyElements() {
    val many = 1024
    val table = new ReactHashMap[Long, String]
    for (i <- 0 until many) table(i) = i.toString

    table.size should equal (many)
    for (i <- 0 until many) table(i) should equal (i.toString)
    for (i <- 0 until many / 2) table.remove(i) should equal (true)
    for (i <- 0 until many / 2) table.get(i) should equal (None)
    for (i <- many / 2 until many) table(i) should equal (i.toString)
    table.clear()
    for (i <- 0 until many) table.get(i) should equal (None)
    table.size should equal (0)
  }

  it should "subscribe to a specific key" in {
    val many = 512
    val table = new ReactHashMap[Int, String]
    for (i <- 0 until many) table(i) = i.toString
    val specificKey = table.react(128)

    table(128) = "new value"
    specificKey() should equal ("new value")
  }

  it should "subscribe to many keys" in {
    val size = 1024
    val many = 512
    val table = new ReactHashMap[Int, String]
    for (i <- 0 until many) table(i) = i.toString
    val signals = for (i <- 0 until many) yield table.react(i)
    for (i <- 0 until size) table(i) = s"value$i"
    for (i <- 0 until many) signals(i)() should equal (s"value$i")
    val moresignals = for (i <- many until size) yield table.react(i)
    for (i <- many until size) moresignals(i - many)() should equal (s"value$i")
  }

  it should "subscribe to non-existing keys" in {
    testSubscribeNonexisting()
  }

  def testSubscribeNonexisting() {
    val size = 256
    val many = 128
    val table = new ReactHashMap[Int, String]
    val signsOfLife = Array.fill(many)(false)
    val subs = for (i <- 0 until many) yield {
      val values = table.react(i)
      values on {
        signsOfLife(i) = true
      }
    }

    for (i <- 0 until size) table(i) = "foobar"
    for (i <- 0 until many) signsOfLife(i) should equal (true)
  }

  it should "accurately GC key subscriptions no longer used" in {
    val size = 256
    val many = 128
    val table = new ReactHashMap[Int, String]
    val signsOfLife = Array.fill(many)(false)
    for (i <- 0 until many) yield table.react(i)

    sys.runtime.gc()

    for (i <- 0 until size) table(i) = "foobar"
    for (i <- 0 until many) signsOfLife(i) should equal (false)
  }

  it should "contain the correct set of keys" in {
    val size = 256
    val table = new ReactHashMap[Int, String]
    val observed = mutable.Set[Int]()
    val keys = table.keys
    keys.inserts.onEvent(observed += _)
    for (i <- 0 until size) table(i) = i.toString

    observed should equal ((0 until size).toSet)
  }

  it should "contain the correct set of values" in {
    val size = 256
    val table = new ReactHashMap[Int, String]
    val observed = mutable.Set[Int]()
    val keys = table.keys
    val insertSub = keys.inserts.onEvent(observed += _)
    for (i <- 0 until size) table(i) = i.toString

    observed should equal ((0 until size).toSet)
  }

}




