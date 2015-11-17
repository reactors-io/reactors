package scala.reactive
package util



import org.scalatest.FunSuite
import org.scalatest.Matchers



class UnrolledBufferTest extends FunSuite with Matchers {

  test("enqueue and dequeue many elements") {
    val b = new UnrolledBuffer[String]()
    for (i <- 0 until 1000) b.enqueue(i.toString)
    for (i <- 0 until 1000) assert(b.dequeue() == i.toString)
  }

}