package org.reactress
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class CataSignaloidSpec extends FlatSpec with ShouldMatchers {

  this("Monoid", CataSignaloid.commutoid(Commutoid(0)(_ + _))) // TODO
  this("Commutoid", CataSignaloid.commutoid(Commutoid(0)(_ + _)))
  this("Abelian", CataSignaloid.abelian(Abelian(0)(_ + _)(_ - _)))

  def apply(structure: String, newCataSignaloid: =>CataSignaloid[Int]) {
    s"A CataSignaloid using ${structure}s" should "be empty" in {
      val aggregate = newCataSignaloid
  
      aggregate() should equal (0)
    }
  
    it should "accurately reflect a single signal" in {
      val aggregate = newCataSignaloid
      val rc0 = ReactCell(0)
      aggregate += rc0
  
      aggregate() should equal (0)
      rc0 := 1
      aggregate() should equal (1)
      rc0 := 2
      aggregate() should equal (2)
    }
  
    it should "accurately reflect two signals" in {
      val aggregate = newCataSignaloid
      val rc0 = ReactCell(0)
      val rc1 = ReactCell(0)
      aggregate += rc0
      aggregate += rc1
  
      aggregate() should equal (0)
      rc0 := 1
      aggregate() should equal (1)
      rc1 := 2
      aggregate() should equal (3)
      rc0 := 3
      aggregate() should equal (5)
      rc1 := 20
      aggregate() should equal (23)
      rc0 := -21
      aggregate() should equal (-1)
    }
  
    it should "accurately reflect many signals" in {
      val aggregate = newCataSignaloid
      val cells = for (_ <- 0 until 20) yield ReactCell(0)
      for (c <- cells) aggregate += c
  
      aggregate() should equal (0)
      for ((c, i) <- cells.zipWithIndex) c := i
      aggregate() should equal (cells.length * (cells.length - 1) / 2)
      cells(10) := 0
      aggregate() should equal (cells.length * (cells.length - 1) / 2 - 10)
    }
  
    it should "accurately reflect addition of new signals" in {
      val aggregate = newCataSignaloid
      val cells = for (i <- 0 until 50) yield ReactCell(i)
      for (c <- cells) aggregate += c
  
      def total(n: Int) = n * (n - 1) / 2
      aggregate() should equal (total(cells.length))
      aggregate += ReactCell(50)
      aggregate() should equal (total(cells.length + 1))
      aggregate += ReactCell(51)
      aggregate() should equal (total(cells.length + 2))
      aggregate += ReactCell(52)
      aggregate() should equal (total(cells.length + 3))
      aggregate += ReactCell(53)
      aggregate() should equal (total(cells.length + 4))
    }
  
    it should "accurately reflect removal of signals" in {
      val aggregate = newCataSignaloid
      val cells = for (i <- 0 until 50) yield ReactCell(i)
      for (c <- cells) aggregate += c
  
      def total(n: Int) = n * (n - 1) / 2
      aggregate() should equal (total(cells.length))
      for ((c, i) <- cells.reverse.zipWithIndex) {
        aggregate -= c
        aggregate() should equal (total(cells.length - i - 1))
      }
    }
  
    it should "accurately reflect signals being removed and added" in {
      val max = 50
      val aggregate = newCataSignaloid
      val cells = for (i <- 0 until max) yield ReactCell(i)
      for (c <- cells) aggregate += c
  
      def total(n: Int) = n * (n - 1) / 2
      aggregate() should equal (total(cells.length))
      for ((c, i) <- cells.reverse.take(max / 2).zipWithIndex) {
        aggregate -= c
        aggregate() should equal (total(cells.length - i - 1))
      }
      for (i <- (max - max / 2) until max) {
        aggregate += ReactCell(i)
        aggregate() should equal (total(i + 1))
      }
    }

  }

}

