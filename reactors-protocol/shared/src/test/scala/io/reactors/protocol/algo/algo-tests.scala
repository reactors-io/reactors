package io.reactors
package protocol.algo



import io.reactors.test._
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._



class AlgoSpec extends FunSuite {
  test("reservoir sampling, no events") {
    val e = new Events.Emitter[Int]
    val sample = e.reservoirSample(5)
    e.unreact()
    assert(sample().length == 0)
  }

  test("reservoir sampling, less than k") {
    val e = new Events.Emitter[Int]
    val sample = e.reservoirSample(5)
    e.react(7)
    e.react(17)
    e.unreact()
    assert(sample().toSeq == Seq(7, 17))
  }

  test("reservoir sampling, more than k") {
    val e = new Events.Emitter[Int]
    val sample = e.reservoirSample(5)
    val elems = (0 until 16)
    for (i <- elems) e.react(i)
    e.unreact()
    assert(sample().toSeq.length == 5)
    assert(sample().toSeq.forall(x => elems.toSet.contains(x)))
    assert(sample().distinct.length == 5)
  }
}
