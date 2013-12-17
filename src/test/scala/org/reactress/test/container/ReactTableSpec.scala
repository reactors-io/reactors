package org.reactress
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactTableSpec extends FlatSpec with ShouldMatchers {

  "A ReactTable" should "be empty" in {
    val table = new ReactTable[Long, Int]

    table.size should equal (0)
    table.get(0L) should equal (None)
    evaluating { table(0L) } should produce [NoSuchElementException]
    table.remove(0L) should equal (false)
  }

  it should "contain a single element" in {
    val table = new ReactTable[Long, Int]
    table(2L) = 2L.toInt

    table.size should equal (1)
    table.get(2L) should equal (Some(2L.toInt))
    table.apply(2L) should equal (2L.toInt)

    table.remove(2L) should equal (true)
    table.size should equal (0)
  }

  it should "contain two elements" in {
    val table = new ReactTable[Long, Int]
    table.update(3L, 3L.toInt)
    table.update(4L, 4L.toInt)

    table.size should equal (2)
    table.get(3L) should equal (Some(3L.toInt))
    table.apply(4L) should equal (4L.toInt)
    table.get(5L) should equal (None)
  }

  it should "contain several elements" in {
    val table = new ReactTable[Int, Int]
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

  it should "contain many elements" in {
    val many = 1024
    val table = new ReactTable[Long, Int]
    for (i <- 0 until many) table(i) = i.toInt

    table.size should equal (many)
    for (i <- 0 until many) table(i) should equal (i.toInt)
    for (i <- 0 until many / 2) table.remove(i) should equal (true)
    for (i <- 0 until many / 2) table.get(i) should equal (None)
    for (i <- many / 2 until many) table(i) should equal (i.toInt)
    table.clear()
    for (i <- 0 until many) table.get(i) should equal (None)
    table.size should equal (0)
  }

}



