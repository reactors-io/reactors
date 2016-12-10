package io.reactors
package protocol



import scala.collection._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.concurrent.Promise



class StreamingLibraryTest extends AsyncFunSuite with AsyncTimeLimitedTests {
  val system = ReactorSystem.default("streaming-lib")

  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  import StreamingLibraryTest._

  test("streaming map") {
    val total = 4096
    val done = Promise[Seq[Int]]()

    system.spawnLocal[Unit] { self =>
      val seen = mutable.Buffer[Int]()
      val source = new Source[Int](system)
      val ready = source.map(_ * 2).foreach { x =>
        seen += x
        if (seen.size == total) done.success(seen)
      }

      (ready ? ()) on {
        var i = 0
        source.valve.available.is(true) on {
          while (source.valve.available() && i < total) {
            source.valve.channel ! i
            i += 1
          }
        }
      }
    }

    done.future.map(t => assert(t == (0 until total).map(_ * 2)))
  }
}


object StreamingLibraryTest {
  type StreamReq[T] = Channel[Reliable.TwoWay.Req[Int, T]]

  type StreamServer[T] = Channel[StreamReq[T]]

  type StreamMedium[T] = Backpressure.Medium[Reliable.TwoWay.Req[Int, T], T]

  trait Stream[T] {
    def system: ReactorSystem

    def streamServer: StreamServer[T]

    def backpressureMedium[R: Arrayable]: StreamMedium[R]

    def backpressurePolicy: Backpressure.Policy

    def map[S](f: T => S)(implicit at: Arrayable[T], as: Arrayable[S]): Stream[S] =
      new Mapped(this, f)

    def foreach(f: T => Unit)(implicit a: Arrayable[T]): Server[Unit, Unit] = {
      val medium = backpressureMedium[T]
      val policy = backpressurePolicy
      system.spawnLocal[Server.Req[Unit, Unit]] { self =>
        val server = system.channels.backpressureServer(medium)
          .serveBackpressure(medium, policy)
        streamServer ! server.channel
        val incoming = server.connections.once
        incoming onEvent { pump =>
          pump.buffer.onEvent(f)
          pump.buffer.available.is(true) on {
            while (pump.buffer.nonEmpty) pump.buffer.dequeue()
          }
        }
        self.main.events.defer(incoming) onMatch {
          case (_, ch) => ch ! ()
        }
      }
    }
  }

  class Source[T: Arrayable](val system: ReactorSystem) extends Stream[T] {
    def backpressureMedium[R: Arrayable] =
      Backpressure.Medium.reliable[R](Reliable.TwoWay.Policy.reorder(256))

    val backpressurePolicy = Backpressure.Policy.sliding(256)

    val (valve, streamServer) = {
      val multi = new MultiValve[T](256)

      val server = system.channels.open[StreamReq[T]]
      server.events.onEvent { bs =>
        bs.connectBackpressure(backpressureMedium[T], backpressurePolicy) onEvent { v =>
          multi += v
        }
      }

      (multi.out, server.channel)
    }
  }

  trait Transformed[T, S] extends Stream[S] {
    implicit val arrayableT: Arrayable[T]

    implicit val arrayableS: Arrayable[S]

    def backpressureMedium[R: Arrayable] = parent.backpressureMedium[R]

    val backpressurePolicy = parent.backpressurePolicy

    def parent: Stream[T]

    def kernel(x: T, output: Channel[S]): Unit

    val streamServer: StreamServer[S] = {
      val inMedium = backpressureMedium[T]
      val outMedium = backpressureMedium[S]
      val policy = backpressurePolicy

      system.spawn(Reactor[StreamReq[S]] { self =>
        val multi = new MultiValve[S](256)
        val server = self.system.channels.backpressureServer(inMedium)
          .serveBackpressureConnections(inMedium, policy)
        parent.streamServer ! server.channel

        val incoming = server.connections.once
        incoming onEvent { c =>
          val available = (c.buffer.available zip multi.out.available)(_ && _)
            .changes.toSignal(false)
          available.is(true) on {
            while (available()) {
              val x = c.buffer.dequeue()
              kernel(x, multi.out.channel)
              c.pressure ! 1
            }
          }
        }

        self.main.events.defer(incoming) onEvent { backServer =>
          backServer.connectBackpressure(outMedium, policy) onEvent {
            valve => multi += valve
          }
        }
      })
    }
  }

  class Mapped[T, S](val parent: Stream[T], val f: T => S)(
    implicit val arrayableT: Arrayable[T], val arrayableS: Arrayable[S]
  ) extends Transformed[T, S] {
    lazy val system = parent.system

    def kernel(x: T, output: Channel[S]): Unit = {
      val y = f(x)
      output ! y
    }
  }

}
