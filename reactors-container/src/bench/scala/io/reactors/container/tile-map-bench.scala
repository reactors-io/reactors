package io.reactors
package container



import org.scalameter.api._
import org.scalameter.japi.JBench
import scala.reactive.RTileMap



trait TileMapBench extends JBench.OfflineReport {

  class Matrix(val width: Int, val height: Int) {
    val array = new Array[Int](width * height)
    def apply(x: Int, y: Int) = array(y * width + x)
  }

  val maxIterations = 400000

  val sidelengths = Gen.range("sidelength")(500, 2500, 500)

  val matrices = for (sz <- sidelengths) yield new Matrix(sz, sz)

  val hashMatrices = for (sz <- sidelengths) yield {
    val hm = new RHashMatrix[Int]
    for (x <- 0 until sz; y <- 0 until sz) hm(x, y) = x * y + 1
    for (x <- 0 until sz; y <- 0 until sz) assert(hm(x, y) != hm.nil, hm(x, y))
    (sz, hm)
  }

  val rTileMaps = for (sz <- sidelengths) yield {
    val tilemap = new RTileMap[Int](sz, 0)
    for (x <- 0 until sz; y <- 0 until sz) tilemap(x, y) = x * y
    (sz, tilemap)
  }

  override def defaultConfig = Context(
    exec.minWarmupRuns -> 20,
    exec.maxWarmupRuns -> 40,
    exec.benchRuns -> 8,
    exec.independentSamples -> 1
  )

  @volatile var load = 0

  @gen("matrices")
  @benchmark("tilemap.apply")
  @curve("Matrix")
  def matrixApply(matrix: Matrix) {
    var i = 0
    var y = 0
    while (y < matrix.height) {
      var x = 0
      while (x < matrix.width) {
        load = matrix(x, y)
        x += 1
        i += 1
        if (i > maxIterations) return
      }
      y += 1
    }
  }

  def outputHashMatrixStats(p: (Int, RHashMatrix[Int])) {
    val stats = p._2.matrix.debugBlockMap
    val collisions = stats.groupBy(_._2.length).map({ case (k, v) => (k, v.size) })
    println("collisions: " + collisions.mkString(", "))
    val numBlocks = stats.map(_._2.length).sum
    println("num blocks:" + numBlocks)
  }

  @gen("hashMatrices")
  @benchmark("tilemap.apply")
  @curve("HashMatrix")
  def hashMatrixApply(p: (Int, RHashMatrix[Int])) {
    val sidelength = p._1
    val matrix = p._2
    var i = 0
    var y = 0
    while (y < sidelength) {
      var x = 0
      while (x < sidelength) {
        load = matrix(x, y)
        x += 1
        i += 1
        if (i > maxIterations) return
      }
      y += 1
    }
  }

  @gen("rTileMaps")
  @benchmark("tilemap.apply")
  @curve("RTileMap")
  def tileMapApply(p: (Int, RTileMap[Int])) {
    val sidelength = p._1
    val tilemap = p._2
    var i = 0
    var y = 0
    while (y < sidelength) {
      var x = 0
      while (x < sidelength) {
        load = tilemap(x, y)
        x += 1
        i += 1
        if (i > maxIterations) return
      }
      y += 1
    }
  }

  val array = new Array[Int](62500000)

  @gen("matrices")
  @benchmark("tilemap.copy")
  @curve("Matrix")
  def matrixCopy(matrix: Matrix) {
    val len = matrix.width * matrix.height
    System.arraycopy(matrix.array, 0, array, 0, len)
  }

  @gen("hashMatrices")
  @benchmark("tilemap.copy")
  @curve("HashMatrix")
  def hashMatrixCopy(p: (Int, RHashMatrix[Int])) {
    val (sidelength, matrix) = p
    matrix.copy(array, 0, 0, sidelength, sidelength)
  }

  @gen("rTileMaps")
  @benchmark("tilemap.copy")
  @curve("RTileMap")
  def tileMapCopy(p: (Int, RTileMap[Int])) {
    val (sidelength, tilemap) = p
    tilemap.read(array, sidelength, sidelength, 0, 0, sidelength, sidelength)
  }
}


class TileMapBenches extends Bench.Group {

  include(new TileMapBench {})

}
