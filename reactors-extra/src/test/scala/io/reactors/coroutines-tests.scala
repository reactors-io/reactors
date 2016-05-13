package io.reactors



import io.reactors.coroutines._
import org.coroutines._
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._



class CoroutinesTest extends FunSuite with Matchers with BeforeAndAfterAll {
  val system = ReactorSystem.default("test-system")

  test("reactor declared with a coroutine") {
    val proto = Reactor.coroutine(coroutine { (self: Reactor[String]) =>
      val x = self.main.events.receive()
    })
  }

  override def afterAll() {
    system.shutdown()
  }
}
