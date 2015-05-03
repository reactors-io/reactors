package scala.reactive
package container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import java.util.NoSuchElementException
import scala.collection._



class RBinaryHeapSpec extends FlatSpec with ShouldMatchers {

  "A RBinaryHeap" should "react to head changes" in {
    val size = 200
    val q = new RBinaryHeap[Int]
    val buffer = mutable.Buffer[Int]()
    val heads = q.react.head.foreach(buffer += _)

    for (i <- (0 until size).reverse) q.enqueue(i)

    buffer should equal ((0 until size).reverse)

    for (i <- 0 until size) {
      q.dequeue() should equal (i)
      if (i < (size - 1)) {
        q.head should equal (i + 1)
      }
    }
  }

}




