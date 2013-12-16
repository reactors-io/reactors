package org.reactress
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactCatamorphSpec extends FlatSpec with ShouldMatchers {

  "ReactCommutoid" should "be balanced" in {
    val tree = ReactCommutoid(Commutoid(0)(_ + _))

    for (i <- 0 until 10) tree += i

    def check(node: ReactCommutoid.Node[Int]): Unit = node match {
      case in: ReactCommutoid.Inner[Int] =>
        in.height should equal (1 + math.max(in.left.height, in.right.height))
        in.left.height should (be (in.height - 1) or be (in.height - 2))
        in.right.height should (be (in.height - 1) or be (in.height - 2))
        check(in.left)
        check(in.right)
      case leaf: ReactCommutoid.Leaf[Int] =>
        leaf.height should equal (0)
      case empty: ReactCommutoid.Empty[Int] =>
        empty.height should equal (0)
    }
  }

}