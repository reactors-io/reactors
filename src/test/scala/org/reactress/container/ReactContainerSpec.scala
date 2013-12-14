package org.reactress
package container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactContainerSpec extends FlatSpec with ShouldMatchers {

  "A ReactContainer" should "map" in {
    val numbers = new ReactSet[Long]
    val mapped = numbers.map(-_).forced
    
    mapped.size should equal (0)

    numbers += 1L

    mapped.size should equal (1)
    mapped(-1L) should equal (true)

    for (n <- 2 until 20) numbers += n.toLong
    for (n <- 2 until 20) mapped(-n) should equal (true)
  }

  it should "be mapped into a different container" in {
    val numbers = new ReactSet[Int]
    val mapped = numbers.map(2 * _).forcedTo[ReactSet[Int]]

    mapped.size should equal (0)

    numbers += 10

    mapped(10) should equal (false)
    mapped(20) should equal (true)
  }

  it should "filter" in {
    val numbers = new ReactSet[Int]
    val filtered = numbers.filter(_ % 2 == 0).forced

    filtered.size should equal (0)

    for (n <- 0 until 20) numbers += n
    for (n <- 0 until 20) filtered(n) should equal (n % 2 == 0)
  }

  it should "collect" in {
    val numbers = new ReactSet[Int]
    val collected = numbers collect {
      case x if x % 2 == 0 => x
    } forced

    collected.size should equal (0)

    for (n <- 0 until 20) numbers += n
    for (n <- 0 until 20) collected(n) should equal (n % 2 == 0)
  }

  it should "aggregate" in {
    val numbers = new ReactSet[Int]
    val sum = numbers.fold(0)(_ + _).signal(0)

    sum() should equal (0)

    for (n <- 1 until 20) {
      numbers += n
      sum() should equal (n * (n + 1) / 2)
    }
  }

}
