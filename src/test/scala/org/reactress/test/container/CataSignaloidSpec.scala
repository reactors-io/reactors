package org.reactress
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class CataSignaloidSpec extends FlatSpec with ShouldMatchers {

  plus("Int Monoid", CataSignaloid(Monoid(0)(_ + _)))

  concat("String Monoid", CataSignaloid(Monoid("")(_ + _)))

  plus("Int Commutoid", CataSignaloid(Commutoid(0)(_ + _)))

  plus("Int Abelian", CataSignaloid(Abelian(0)(_ + _)(_ - _)))

  def concat(structure: String, newCataSignaloid: =>CataSignaloid[String]) {
    s"A CataSignaloid using ${structure}s" should "correctly reflect added signals" in {
      val catamorph = newCataSignaloid
      val rc1 = ReactCell("a")
      val rc2 = ReactCell("b")
      catamorph += rc1
      catamorph += rc2

      catamorph.signal() should equal ("ab")
    }

    it should "correctly reflect many added signals" in {
      val catamorph = newCataSignaloid
      val cells = for (i <- 0 until 100) yield new ReactCell(i + " ")
      for ((c, i) <- cells.zipWithIndex) {
        catamorph += c
        catamorph.signal() should equal ((0 to i).foldLeft("")(_ + _ + " "))
      }
    }

    it should "correctly reflect removing signals" in {
      val catamorph = newCataSignaloid
      val cells = for (i <- 0 until 50) yield new ReactCell(i + " ")
      for (c <- cells) catamorph += c
      for ((c, i) <- cells.zipWithIndex; if (i % 2 == 0)) {
        catamorph -= c
        val expected = (0 until 50).filter(x => x % 2 == 1 || x > i).foldLeft("")(_ + _ + " ")
        catamorph.signal() should equal (expected)
      }
    }
  }

  def plus(structure: String, newCataSignaloid: =>CataSignaloid[Int]) {
    s"A CataSignaloid using ${structure}s" should "be empty" in {
      val catamorph = newCataSignaloid
  
      catamorph.signal() should equal (0)
    }
  
    it should "accurately reflect a single signal" in {
      val catamorph = newCataSignaloid
      val rc0 = ReactCell(0)
      catamorph += rc0
  
      catamorph.signal() should equal (0)
      rc0 := 1
      catamorph.signal() should equal (1)
      rc0 := 2
      catamorph.signal() should equal (2)
    }
  
    it should "accurately reflect two signals" in {
      val catamorph = newCataSignaloid
      val rc0 = ReactCell(0)
      val rc1 = ReactCell(0)
      catamorph += rc0
      catamorph += rc1
  
      catamorph.signal() should equal (0)
      rc0 := 1
      catamorph.signal() should equal (1)
      rc1 := 2
      catamorph.signal() should equal (3)
      rc0 := 3
      catamorph.signal() should equal (5)
      rc1 := 20
      catamorph.signal() should equal (23)
      rc0 := -21
      catamorph.signal() should equal (-1)
    }
  
    it should "accurately reflect many signals" in {
      val catamorph = newCataSignaloid
      val cells = for (_ <- 0 until 20) yield ReactCell(0)
      for (c <- cells) catamorph += c
  
      catamorph.signal() should equal (0)
      for ((c, i) <- cells.zipWithIndex) c := i
      catamorph.signal() should equal (cells.length * (cells.length - 1) / 2)
      cells(10) := 0
      catamorph.signal() should equal (cells.length * (cells.length - 1) / 2 - 10)
    }
  
    it should "accurately reflect addition of new signals" in {
      val catamorph = newCataSignaloid
      val cells = for (i <- 0 until 50) yield ReactCell(i)
      for (c <- cells) catamorph += c
  
      def total(n: Int) = n * (n - 1) / 2
      catamorph.signal() should equal (total(cells.length))
      catamorph += ReactCell(50)
      catamorph.signal() should equal (total(cells.length + 1))
      catamorph += ReactCell(51)
      catamorph.signal() should equal (total(cells.length + 2))
      catamorph += ReactCell(52)
      catamorph.signal() should equal (total(cells.length + 3))
      catamorph += ReactCell(53)
      catamorph.signal() should equal (total(cells.length + 4))
    }
  
    it should "accurately reflect removal of signals" in {
      val catamorph = newCataSignaloid
      val cells = for (i <- 0 until 50) yield ReactCell(i)
      for (c <- cells) catamorph += c
  
      def total(n: Int) = n * (n - 1) / 2
      catamorph.signal() should equal (total(cells.length))
      for ((c, i) <- cells.reverse.zipWithIndex) {
        catamorph -= c
        catamorph.signal() should equal (total(cells.length - i - 1))
      }
    }
  
    it should "accurately reflect signals being removed and added" in {
      val max = 50
      val catamorph = newCataSignaloid
      val cells = for (i <- 0 until max) yield ReactCell(i)
      for (c <- cells) catamorph += c
  
      def total(n: Int) = n * (n - 1) / 2
      catamorph.signal() should equal (total(cells.length))
      for ((c, i) <- cells.reverse.take(max / 2).zipWithIndex) {
        catamorph -= c
        catamorph.signal() should equal (total(cells.length - i - 1))
      }
      for (i <- (max - max / 2) until max) {
        catamorph += ReactCell(i)
        catamorph.signal() should equal (total(i + 1))
      }
    }

  }

}

