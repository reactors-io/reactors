package org.reactress
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactTileMapSpec extends FlatSpec with ShouldMatchers {

  "A ReactTileMap" should "have dimension 128" in {
    val table = new ReactTileMap[String](128, "")
    table.dimension should equal (128)
    table(30, 30) should equal ("")
    table(90, 90) should equal ("")
  }

  it should "contain a single element" in {
    val table = new ReactTileMap[String](128, "")

    table(0, 0) = "ok"

    table.dimension should equal (128)
    table(0, 0) should equal ("ok")
    table(1, 1) should equal ("")
    table(56, 72) should equal ("")
  }

  it should "contain a rectangle in the middle" in {
    val table = new ReactTileMap[String](128, "")
    for (x <- 50 until 70; y <- 55 until 75) table(x, y) = (x, y).toString

    for (x <- 0 until 128; y <- 0 until 128) assert(table(x, y) == (
      if (x >= 50 && x < 70 && y >= 55 && y < 75) (x, y).toString
      else ""
    ))
    
  }

  it should "be big and contain a checkerboard" in {
    val big = 1024
    val table = new ReactTileMap[Int](1024, 0)
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
    val table = new ReactTileMap[Int](1024, 0)
    for (x <- 0 until big by 2; y <- 0 until big by 2) table(x, y) = 1

    table.clear()
    assert(table(600, 150) == 0)
    assert(table.quadRoot.isLeaf)
  }

  it should "detect modifications" in {
    import org.reactress._
    val size = 32
    val tilemap = new ReactTileMap[String](size, null)
    val m = tilemap.updates
    val a = m onEvent { _ =>
      assert(tilemap(0, 0) == "OK")
    }

    tilemap(0, 0) = "OK"
  }

}
















