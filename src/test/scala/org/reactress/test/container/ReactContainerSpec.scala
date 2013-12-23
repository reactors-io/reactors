package org.reactress
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactContainerSpec extends FlatSpec with ShouldMatchers {

  "A ReactContainer" should "map" in { map() }

  def map() {
    val numbers = new ReactSet[Long]
    val mapped = numbers.react.map(-_).to[ReactSet[Long]]
    
    sys.runtime.gc()

    mapped.size should equal (0)

    numbers += 1L

    mapped.size should equal (1)
    mapped(-1L) should equal (true)

    for (n <- 2 until 20) numbers += n.toLong
    for (n <- 2 until 20) mapped(-n) should equal (true)
  }

  it should "be mapped into a different container" in {
    val numbers = new ReactSet[Int]
    val mapped = numbers.react.map(2 * _).to[ReactSet[Int]]

    mapped.size should equal (0)

    numbers += 10

    mapped(10) should equal (false)
    mapped(20) should equal (true)
  }

  it should "filter" in {
    val numbers = new ReactSet[Int]
    val filtered = numbers.react.filter(_ % 2 == 0).to[ReactSet[Int]]

    filtered.size should equal (0)

    for (n <- 0 until 20) numbers += n
    for (n <- 0 until 20) filtered(n) should equal (n % 2 == 0)
  }

  it should "aggregate" in {
    val numbers = new ReactSet[Int]
    val sum = numbers.react.aggregate(Commutoid(0)(_ + _))

    sum() should equal (0)

    for (n <- 1 until 20) {
      numbers += n
      sum() should equal (n * (n + 1) / 2)
    }
  }

  it should "aggregate using a typeclass" in {
    import algebra.structure.setUnion
    val numbers = new ReactSet[Set[Int]]
    val union = numbers.react.aggregate

    union() should equal (Set())

    for (n <- 1 until 20) {
      numbers += Set(n)
      union() should equal (Set() ++ (1 to n))
    }
  }

}
