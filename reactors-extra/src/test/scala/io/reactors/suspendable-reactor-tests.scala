package io.reactors



import io.reactors.suspendable._
import org.coroutines._
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._



class SuspendableReactorTest extends FunSuite with Matchers with BeforeAndAfterAll {
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

  test("reactor terminate after first message") {
    val done = Promise[Boolean]()
    val ch = system.spawn(Reactor.suspendable { (self: Reactor[String]) =>
      val x = self.main.events.receive()
      if (x == "terminate") {
        done.success(true)
        self.main.seal()
      }
    })
    ch ! "terminate"
    assert(Await.result(done.future, 10.seconds))
  }

  override def afterAll() {
    system.shutdown()
  }
}
