package org.reactress
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactCatamorphSpec extends FlatSpec with ShouldMatchers {

  "CataCommutoid" should "be balanced" in {
    val tree = CataCommutoid(Commutoid(0)(_ + _))

    for (i <- 0 until 10) tree += i

    def check(node: CataCommutoid.Node[Int]): Unit = node match {
      case in: CataCommutoid.Inner[Int] =>
        in.height should equal (1 + math.max(in.left.height, in.right.height))
        in.left.height should (be (in.height - 1) or be (in.height - 2))
        in.right.height should (be (in.height - 1) or be (in.height - 2))
        check(in.left)
        check(in.right)
      case leaf: CataCommutoid.Leaf[Int] =>
        leaf.height should equal (0)
      case empty: CataCommutoid.Empty[Int] =>
        empty.height should equal (0)
    }
  }

}