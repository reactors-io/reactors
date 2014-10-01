package scala.reactive
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import java.util.NoSuchElementException
import scala.collection._



class RUnrolledQueueSpec extends FlatSpec with ShouldMatchers {

  "A RUnrolledQueue" should "react to head changes" in {
    val size = 200
    val q = new RUnrolledQueue[Int]
    val buffer = mutable.Buffer[Int]()
    val heads = q.react.head.onEvent(buffer += _)

    for (i <- 0 until size) q.enqueue(i)

    buffer should equal (0 until 1)

    for (i <- 0 until size) {
      q.dequeue() should equal (i)
      if (i < (size - 1)) {
        q.head should equal (i + 1)
        buffer should equal (0 to (i + 1))
      }
    }
  }

}




