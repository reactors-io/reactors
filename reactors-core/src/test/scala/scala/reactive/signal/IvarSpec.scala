package scala.reactive
package signal



import scala.collection._
import org.scalatest._



class IvarSpec extends FlatSpec with Matchers {

  "An ivar" should "be assigned" in {
    val iv = new Ivar[Int]
    iv := 5
    assert(iv() == 5)
    assert(iv.isAssigned)
  }

  it should "be unreacted" in {
    val iv = new Ivar[Int]
    iv.unreact()
    assert(iv.isUnreacted)
  }

  it should "throw" in {
    val iv = new Ivar[Int]
    iv.unreact()
    intercept[RuntimeException] {
      iv()
    }
  }

  it should "be completed with the orElse part" in {
    val a = new Ivar[Int]
    val b = a.orElse(5)
    a.unreact()

    b() should equal (5)
  }

  it should "be completed with the orElse part early" in {
    val a = new Ivar[Int]
    a.unreact()
    val b = a.orElse(5)

    b() should equal (5)
  }

  it should "be completed with the source Ivar in orElse" in {
    val a = new Ivar[Int]
    val b = a.orElse(5)
    a := 1

    b() should equal (1)
  }

  it should "be completed with the source Ivar in orElse early" in {
    val a = new Ivar[Int]
    a := 1
    val b = a.orElse(5)

    b() should equal (1)
  }

  it should "be created unreacted" in {
    val a = Ivar.unreacted
    a.isUnreacted should equal (true)
  }

  it should "be completed with the orElseWith part" in {
    val a = new Ivar[Int]
    val b = RCell(0)
    val c = a.orElseWith(b)
    a.unreact()
    b := 11

    c() should equal (11)
  }

  it should "be completed with the source Ivar in orElseWith" in {
    val a = new Ivar[Int]
    val b = RCell(0)
    val c = a.orElseWith(b)
    a := 7
    b := 11

    c() should equal (7)
  }

}

