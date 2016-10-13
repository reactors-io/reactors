package io.reactors
package direct



import io.reactors.protocol._
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class DirectBackpressureTest
extends FunSuite with Matchers with BeforeAndAfterAll {
  val system = ReactorSystem.default("test-system")

  ignore("start backpressure and send messages") {
    val worker = system.backpressurePerClient(50) { (events: Events[Int]) =>
      var sum = 0
      events onEvent {
        sum += _
      }
    }

    val proto = Reactor.direct[Unit] { self =>
      val link = worker.link.receive()
      var i = 0
      while (i < 100) {
        link ! i
        i += 1
      }
    }
  }

  override def afterAll() {
    system.shutdown()
  }
}
