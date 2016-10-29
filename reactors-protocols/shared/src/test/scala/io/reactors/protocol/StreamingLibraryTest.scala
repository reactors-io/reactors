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
    def map[S](f: T => S): Mapped[S] = ???
    def flatMap[S](f: T => Seq[S]): FlatMapped[S] = ???
    def filter(p: T => Boolean): Filtered[T] = ???
    def scanPast[S](op: (S, T) => S): ScannedPast[T] = ???
    def batch(sz: Int): Batched[T] = ???
    def sync[S](that: Stream[S]): Synced[T, S] = ???

  }

  class Mapped[T] extends Stream[T] {
  }

  class Filtered[T] extends Stream[T] {
  }

  class FlatMapped[T] extends Stream[T] {
  }

  class ScannedPast[T] extends Stream[T] {
  }

  class Batched[T] extends Stream[Seq[T]] {
  }

  class Synced[T, S] extends Stream[(T, S)] {
  }
}
