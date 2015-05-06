package scala.reactive
package container



import java.util.NoSuchElementException
import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.testx._
import scala.collection._



class RUnrolledQueueCheck extends Properties("RUnrolledQueue") with ExtendedProperties {

  val sizes = detChoose(0, 1000)

  property("react to head changes") = forAllNoShrink(sizes) { size =>
    stackTraced {
      val q = new RUnrolledQueue[Int]
      val buffer = mutable.Buffer[Int]()
      val heads = q.react.head.foreach(buffer += _)

      for (i <- 0 until size) q.enqueue(i)

      assert(buffer == (0 until 1))

      for (i <- 0 until size) {
        assert(q.dequeue() == i)
        if (i < (size - 1)) {
          assert(q.head == i + 1)
          assert(buffer == (0 to (i + 1)))
        }
      }

      true
    }
  }

}




