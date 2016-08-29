package io.reactors
package suspendable



import org.coroutines._
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class BackpressureReactorTest extends FunSuite with Matchers with BeforeAndAfterAll {
  val system = ReactorSystem.default("test-system")

  test("start backpressure and send messages") {
    // val ch = system.backpressure { (self: Reactor[String]) =>
    // }

    // val proto = Reactor.suspendable { (self: Reactor[String]) =>
      // val ch = worker.request().receive()
      // for (i <- 0 until 100) ch ! job(i)
    // }
  }

  override def afterAll() {
    system.shutdown()
  }
}
