package scala.reactive
package container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class RCatamorphSpec extends FlatSpec with ShouldMatchers {

  "CommuteCatamorph" should "be balanced" in {
    val tree = CommuteCatamorph(Commutoid(0)(_ + _))

    for (i <- 0 until 10) tree += i

    def check(node: CommuteCatamorph.Node[Int]): Unit = node match {
      case in: CommuteCatamorph.Inner[Int] =>
        in.height should equal (1 + math.max(in.left.height, in.right.height))
        in.left.height should (be (in.height - 1) or be (in.height - 2))
        in.right.height should (be (in.height - 1) or be (in.height - 2))
        check(in.left)
        check(in.right)
      case leaf: CommuteCatamorph.Leaf[Int] =>
        leaf.height should equal (0)
      case empty: CommuteCatamorph.Empty[Int] =>
        empty.height should equal (0)
    }
  }

  "MonoidCatamorph" should "be balanced" in {
    val tree = MonoidCatamorph(Monoid(0)(_ + _))

    for (i <- 0 until 10) tree += i

    def check(node: MonoidCatamorph.Node[Int]): Unit = node match {
      case in: MonoidCatamorph.Inner[Int] =>
        in.height should equal (1 + math.max(in.left.height, in.right.height))
        in.left.height should (be (in.height - 1) or be (in.height - 2))
        in.right.height should (be (in.height - 1) or be (in.height - 2))
        check(in.left)
        check(in.right)
      case leaf: MonoidCatamorph.Leaf[Int] =>
        leaf.height should equal (0)
      case empty: MonoidCatamorph.Empty[Int] =>
        empty.height should equal (0)
    }
  }

}