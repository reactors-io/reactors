package scala.reactive
package test.util



import scala.reactive.util.UnrolledRing
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class UnrolledRingSpec extends FlatSpec with ShouldMatchers {

  "UnrolledRing" should "be empty" in {
    val ring = new UnrolledRing[String]

    ring.isEmpty should be (true)
    ring.nonEmpty should be (false)
  }

  it should "enqueue and dequeue an element" in {
    val ring = new UnrolledRing[String]

    ring.enqueue("first")
    ring.isEmpty should be (false)
    ring.head should be ("first")
    ring.dequeue() should be ("first")
    ring.isEmpty should be (true)
  }

  it should "enqueue and dequeue many elements" in {
    val ring = new UnrolledRing[String]

    ring.isEmpty should equal (true)
    for (x <- 0 until 1024) {
      ring.enqueue(x.toString)
      ring.isEmpty should equal (false)
    }
    for (x <- 0 until 1024) {
      ring.isEmpty should equal (false)
      ring.dequeue() should equal (x.toString)
    }
    ring.isEmpty should equal (true)
  }

  it should "randomly enqueue and dequeue elements" in {
    import scala.collection._
    val ring = new UnrolledRing[String]
    val elems = for (x <- 0 until 1024) yield x.toString
    val input = elems.to[mutable.Queue]
    val plan = Seq(4, -2, 4, -2, 8, -4, 16, -16, 64, -8, 64, -128, 32, -32, 64, -64, 512, -256, 128, -256, 64, -192, 32, -16, 32, -48)
    val buffer = mutable.Buffer[String]()

    var count = 0
    for (n <- plan) {
      count += n
      if (n > 0) for (_ <- 0 until n) ring.enqueue(input.dequeue)
      else for (_ <- 0 until -n) buffer += ring.dequeue()
    }
    count should equal (0)

    buffer should equal (elems)
  }

  it should "traverse the elements using foreach" in {
    import scala.collection._
    val ring = new UnrolledRing[Int]
    val queue = mutable.Queue[Int]()
    for (i <- 0 until 200) {
      ring.enqueue(i)
      queue.enqueue(i)
      val buffer = mutable.ArrayBuffer[Int]()
      ring.foreach(x => buffer += x)
      buffer should equal (queue)
      ring.size should equal (queue.size)
    }
  }

}