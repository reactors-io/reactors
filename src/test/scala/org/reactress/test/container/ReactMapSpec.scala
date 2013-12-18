package org.reactress
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactMapSpec extends FlatSpec with ShouldMatchers {

  "A ReactMap" should "be empty" in {
    val table = new ReactMap[Long, String]

    table.size should equal (0)
    table.get(0L) should equal (None)
    evaluating { table(0L) } should produce [NoSuchElementException]
    table.remove(0L) should equal (false)
  }

  it should "contain a single element" in {
    val table = new ReactMap[Long, String]
    table(2L) = 2L.toString

    table.size should equal (1)
    table.get(2L) should equal (Some(2L.toString))
    table.apply(2L) should equal (2L.toString)

    table.remove(2L) should equal (true)
    table.size should equal (0)
  }

  it should "contain two elements" in {
    val table = new ReactMap[Long, String]
    table.update(3L, 3L.toString)
    table.update(4L, 4L.toString)

    table.size should equal (2)
    table.get(3L) should equal (Some(3L.toString))
    table.apply(4L) should equal (4L.toString)
    table.get(5L) should equal (None)
  }

  it should "contain several elements" in {
    val table = new ReactMap[String, String]
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
    val many = 1024
    val table = new ReactMap[Long, String]
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

}




