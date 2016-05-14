package io.reactors



import io.reactors.coroutines._
import org.coroutines._
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._



class CoroutineReactorTest extends FunSuite with Matchers with BeforeAndAfterAll {
  val system = ReactorSystem.default("test-system")

  test("reactor defined from a coroutine") {
    val proto = Reactor.fromCoroutine(coroutine { (self: Reactor[String]) =>
      val x = self.main.events.receive()
    })
  }

  test("reactor defined with suspendable") {
    val proto = Reactor.suspendable { (self: Reactor[String]) =>
      val x = self.main.events.receive()
    }
  }

  override def afterAll() {
    system.shutdown()
  }
}
