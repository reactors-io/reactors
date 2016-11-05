package io.reactors
package protocol



import scala.collection._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
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
  type StreamReq[T] = Channel[Reliable.TwoWay.Req[T, Int]]

  type StreamServer[T] = Channel[StreamReq[T]]

  trait Stream[T] {
    def map[S](f: T => S)(
      implicit at: Arrayable[T], as: Arrayable[S]
    ): Mapped[T, S] = new Mapped(this, f)
//    def flatMap[S](f: T => Seq[S]): FlatMapped[T, S] = new FlatMapped(this, f)
//    def filter(p: T => Boolean): Filtered[T] = new Filtered(this, p)
//    def scanPast[S](op: (S, T) => S): ScannedPast[T, S] = new ScannedPast(this, op)
//    def batch(size: Int): Batched[T] = new Batched(this, size)
//    def sync[S](that: Stream[S]): Synced[T, S] = new Synced(this, that)

    def run(system: ReactorSystem): StreamServer[T]

    def consume(system: ReactorSystem, f: T => Unit)(
      implicit a: Arrayable[T]
    ): Unit = {
      val streamServer = run(system)
      val medium = Backpressure.Medium.reliable[T]
      val policy = Backpressure.Policy.sliding[T](128)
      system.backpressureServer(medium, policy) { server =>
        streamServer ! server.channel
        server.connections.once onEvent { pump =>
          pump.buffer.onEvent(f)
          pump.buffer.available.filter(_ == true) on {
            while (pump.buffer.nonEmpty) pump.buffer.dequeue()
          }
        }
      }
    }
  }

  class Mapped[T: Arrayable, S: Arrayable](val source: Stream[T], val f: T => S)
  extends Stream[S] {
    val inMedium = Backpressure.Medium.reliable[T]
    val inPolicy = Backpressure.Policy.sliding[T](128)
    val outMedium = Backpressure.Medium.reliable[S]
    val outPolicy = Backpressure.Policy.sliding[S](128)

    def run(system: ReactorSystem): StreamServer[S] = {
//      val streamServer = source.run(system)
//      val proto = Reactor[StreamReq[S]] { self =>
//        self.main.events onEvent { outServer =>
//          outServer.connectBackpressure(outMedium, outPolicy) onEvent { link =>
//            val inServer = system.channels.daemon.backpressureServer(inMedium)
//              .serveBackpressure(inMedium, inPolicy)
//            streamServer ! inServer.channel
//
//            inServer.connections.once onEvent { connection =>
//              val incoming = connection.events.map(f)
//              // TODO: Use the link to forward incoming events
//            }
//          }
//        }
//      }
//      system.spawn(proto)
      ???
    }
  }

//  class FlatMapped[T, S](val source: Stream[T], val f: T => Seq[S])
//  extends Stream[S] {
//  }
//
//  class Filtered[T](val source: Stream[T], val p: T => Boolean)
//  extends Stream[T] {
//  }
//
//  class ScannedPast[T, S](val source: Stream[T], val op: (S, T) => S)
//  extends Stream[S] {
//  }
//
//  class Batched[T](val source: Stream[T], val size: Int)
//  extends Stream[Seq[T]] {
//  }
//
//  class Synced[T, S](val source: Stream[T], val that: Stream[S])
//  extends Stream[(T, S)] {
//  }
}
