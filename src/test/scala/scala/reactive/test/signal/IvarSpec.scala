package scala.reactive
package test.signal



import scala.collection._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class IvarSpec extends FlatSpec with ShouldMatchers {

  "An ivar" should "be assigned" in {
    val iv = new Reactive.Ivar[Int]
    iv := 5
    assert(iv() == 5)
    assert(iv.isAssigned)
  }

  it should "be unreacted" in {
    val iv = new Reactive.Ivar[Int]
    iv.unreact()
    assert(iv.isUnreacted)
  }

  it should "throw" in {
    val iv = new Reactive.Ivar[Int]
    iv.unreact()
    intercept[RuntimeException] {
      iv()
    }
  }

}

