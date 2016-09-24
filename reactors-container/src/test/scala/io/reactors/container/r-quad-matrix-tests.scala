package io.reactors
package container



import java.util.NoSuchElementException
import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.scalatest._
import io.reactors.algebra._
import io.reactors.common.Matrix
import io.reactors.test._
import scala.collection._



class RQuadMatrixSpec extends FunSuite {
  test("disallow clear after asMap") {
    val matrix = new RQuadMatrix[Long]
    matrix(0, 0) = 7
    assert(matrix(0, 0) == 7)
    matrix.clear()
    assert(matrix(0, 0) == matrix.nil)
    matrix.asMap
    intercept[IllegalStateException] {
      matrix.clear()
    }
  }
}
