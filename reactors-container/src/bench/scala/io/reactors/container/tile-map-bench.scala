package io.reactors
package container



import io.reactors.common.HashMatrix
import org.scalameter.api._
import org.scalameter.japi.JBench
import scala.reactive.RTileMap



trait TileMapBench extends JBench.OfflineReport {

  class Matrix(val width: Int, val height: Int) {
    private val array = new Array[Int](width * height)
    def apply(x: Int, y: Int) = array(y * width + x)
  }

  val sidelengths = Gen.range("sidelength")(500, 1000, 100)

  val matrices = for (sz <- sidelengths) yield new Matrix(sz, sz)

  val hashMatrices = for (sz <- sidelengths) yield {
    val matrix = new HashMatrix[Int]
    for (x <- 0 until sz; y <- 0 until sz) matrix(x, y) = x * y
    (sz, matrix)
  }

  val rTileMaps = for (sz <- sidelengths) yield {
    val tilemap = new RTileMap[Int](sz, 0)
    for (x <- 0 until sz; y <- 0 until sz) tilemap(x, y) = x * y
    (sz, tilemap)
  }

  override def defaultConfig = Context(
    exec.benchRuns -> 8,
    exec.independentSamples -> 1
  )

  @volatile var load = 0

  // @gen("matrices")
  // @benchmark("tilemap.indexing")
  // @curve("matrix")
  // def matrixApply(matrix: Matrix) {
  //   var y = 0
  //   while (y < matrix.height) {
  //     var x = 0
  //     while (x < matrix.width) {
  //       load = matrix(x, y)
  //       x += 1
  //     }
  //     y += 1
  //   }
  // }

  @gen("hashMatrices")
  @benchmark("tilemap.indexing")
  @curve("hash-matrix")
  def hashMatrixApply(p: (Int, HashMatrix[Int])) {
    val sidelength = p._1
    val matrix = p._2
    var y = 0
    while (y < sidelength) {
      var x = 0
      while (x < sidelength) {
        load = matrix(x, y)
        x += 1
      }
      y += 1
    }
  }

  // @gen("rTileMaps")
  // @benchmark("tilemap.indexing")
  // @curve("RTileMap")
  // def tileMapApply(p: (Int, RTileMap[Int])) {
  //   val sidelength = p._1
  //   val tilemap = p._2
  //   var y = 0
  //   while (y < sidelength) {
  //     var x = 0
  //     while (x < sidelength) {
  //       load = tilemap(x, y)
  //       x += 1
  //     }
  //     y += 1
  //   }
  // }

}

class TileMapBenches extends Bench.Group {

  include(new TileMapBench {})

}
