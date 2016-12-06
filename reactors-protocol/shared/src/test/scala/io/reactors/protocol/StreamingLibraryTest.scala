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
  type StreamReq[T] = Channel[Reliable.TwoWay.Req[Int, T]]

  type StreamServer[T] = Channel[StreamReq[T]]

  type StreamMedium[T] = Backpressure.Medium[Reliable.TwoWay.Req[Int, T], T]

  trait Stream[T] {
    def system: ReactorSystem

    def streamServer: StreamServer[T]

    def backpressureMedium[T: Arrayable]: StreamMedium[T]

    def backpressurePolicy: Backpressure.Policy

    def map[S](f: T => S)(implicit at: Arrayable[T], as: Arrayable[S]): Stream[S] =
      new Mapped(this, f)

    def foreach(f: T => Unit)(implicit a: Arrayable[T]): Unit = {
      val medium = Backpressure.Medium.reliable[T](Reliable.TwoWay.Policy.reorder(128))
      val policy = Backpressure.Policy.sliding(128)
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

  class Source[T](val system: ReactorSystem) extends Stream[T] {
    val streamServer = ???

    val backpressurePolicy = Backpressure.Policy.sliding(128)

    def backpressureMedium[T: Arrayable] =
      Backpressure.Medium.reliable[T](Reliable.TwoWay.Policy.reorder(128))
  }

  class Mapped[T, S](source: Stream[T], f: T => S)(
    implicit val at: Arrayable[T], as: Arrayable[S]
  ) extends Stream[S] {
    val system = source.system

    def backpressureMedium[T: Arrayable] = source.backpressureMedium[T]

    val backpressurePolicy = source.backpressurePolicy

    val streamServer: StreamServer[S] = {
      val inMedium = backpressureMedium[T]
      val outMedium = backpressureMedium[S]
      val policy = backpressurePolicy

      system.spawn(Reactor[StreamReq[S]] { self =>
        val valves = new MultiValve[S](128)

        self.main.events onEvent { backServer =>
          backServer.connectBackpressure(outMedium, policy) onEvent {
            valve => valves += valve
          }
        }

        val server = self.system.channels.backpressureServer(inMedium)
          .serveBackpressureConnections(inMedium, policy)
        source.streamServer ! server.channel

        server.connections.once onEvent { c =>
          val available =
            (c.buffer.available zip valves.out.available) (_ && _).toSignal(false)
          available.is(true) on {
            while (available()) {
              val x = c.buffer.dequeue()
              val y = f(x)
              valves.out.channel ! y
            }
          }
        }
//          def loop(): Unit = {
//            if (connection.buffer.available()) {
//              val x = connection.buffer.dequeue()
//              val y = f(x)
//              val pushes = for (v <- valves.toEvents) yield {
//                if (v.available()) {
//                  v.channel ! y
//                  new Events.Never[Unit]
//                } else {
//                  v.available.filter(_ == true).once.map(_ => v.channel ! y)
//                }
//              }
//              pushes.union onDone {
//                connection.pressure ! 1
//                loop()
//              }
//            } else connection.buffer.available.filter(_ == true).once on {
//              loop()
//            }
//          }
//          loop()
//        }
      })
    }
  }

}
