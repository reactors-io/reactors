package io.reactors.common.concurrent



import io.reactors.test._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Properties
import org.scalatest.FunSuite
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Random



class ByteswapTreeTest extends FunSuite {
  private val random = new Random(121)

  test("pass layout checks") {
    ByteswapTree
  }

  test("ordered insert to a leaf") {
    val tree = new ByteswapTree[Integer, Integer]
    for (i <- 0 until 15) {
      assert(tree.debugLeafInsert(i, i), s"Round $i")
      println(tree.debugLeaf)
      tree.assertLeafInvariants(s"Round $i",
        _ == _.asInstanceOf[ByteswapTree.Item[Integer, Integer]].key)
    }
  }

  test("shuffled insert to a leaf") {
    val tree = new ByteswapTree[Integer, Integer]
    for (i <- random.shuffle((0 until 15).toList)) {
      tree.debugLeafInsert(i, i)
      println(tree.debugLeaf)
      tree.assertLeafInvariants(s"Round $i",
        _ == _.asInstanceOf[ByteswapTree.Item[Integer, Integer]].key)
    }
  }
}
