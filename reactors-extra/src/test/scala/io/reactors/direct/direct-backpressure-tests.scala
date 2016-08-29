package io.reactors
package direct



import org.coroutines._
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class DirectBackpressureTest
extends FunSuite with Matchers with BeforeAndAfterAll {
  val system = ReactorSystem.default("test-system")

  test("start backpressure and send messages") {
    // val worker = system.backpressure { (self: Reactor[String]) =>
    // }

    // val proto = Reactor.direct { (self: Reactor[String]) =>
      // val link = (worker ? {}).receive()
      // for (i <- 0 until 100) link ! job(i)
    // }
  }

  override def afterAll() {
    system.shutdown()
  }
}
