package io.reactors
package protocol



import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests



class StreamingLibraryTest extends AsyncFunSuite with AsyncTimeLimitedTests {
  val system = ReactorSystem.default("streaming-lib")

  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("streaming map") {
    // TODO: Implement test.
    Future(assert(true))
  }
}


object StreamingLibraryTest {
  trait Stream[T] {
    def map[S](f: T => S): Mapped[T, S] = new Mapped(this, f)
    def flatMap[S](f: T => Seq[S]): FlatMapped[T, S] = new FlatMapped(this, f)
    def filter(p: T => Boolean): Filtered[T] = new Filtered(this, p)
    def scanPast[S](op: (S, T) => S): ScannedPast[T, S] = new ScannedPast(this, op)
    def batch(size: Int): Batched[T] = new Batched(this, size)
    def sync[S](that: Stream[S]): Synced[T, S] = new Synced(this, that)

    def output: Channel[Reliable.TwoWay.Req[T, Int]] = ???

    def sink[U](f: T => U): Subscription = {
      ???
    }
  }

  class Mapped[T, S](val source: Stream[T], val f: T => S)
  extends Stream[S] {
  }

  class FlatMapped[T, S](val source: Stream[T], val f: T => Seq[S])
  extends Stream[S] {
  }

  class Filtered[T](val source: Stream[T], val p: T => Boolean)
  extends Stream[T] {
  }

  class ScannedPast[T, S](val source: Stream[T], val op: (S, T) => S)
  extends Stream[S] {
  }

  class Batched[T](val source: Stream[T], val size: Int)
  extends Stream[Seq[T]] {
  }

  class Synced[T, S](val source: Stream[T], val that: Stream[S])
  extends Stream[(T, S)] {
  }
}
