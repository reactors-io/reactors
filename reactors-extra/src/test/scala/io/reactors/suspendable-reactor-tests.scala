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

  test("reactor terminate after n messages") {
    val n = 50
    val done = Promise[Seq[String]]()
    val ch = system.spawn(Reactor.suspendable { (self: Reactor[String]) =>
      val seen = mutable.Buffer[String]()
      var left = n
      while (left > 0) {
        seen += self.main.events.receive()
        left -= 1
      }
      done.success(seen)
      self.main.seal()
    })
    for (i <- 0 until n) ch ! i.toString
    assert(Await.result(done.future, 10.seconds) == (0 until 50).map(_.toString))
  }

  test("reactors play ping-pong") {
    val n = 50
    val done = Promise[Boolean]()

    class PingPong {
      val ping: Channel[String] = system.spawn(Reactor.suspendable {
        (self: Reactor[String]) =>
        val pong = system.spawn(Reactor.suspendable {
          (self: Reactor[String]) =>
          var left = n
          while (left > 0) {
            val x = self.main.events.receive()
            ping ! "pong"
            left -= 1
          }
          self.main.seal()
        })
        var left = n
        while (left > 0) {
          pong ! "ping"
          val x = self.main.events.receive()
          left -= 1
        }
        done.success(true)
        self.main.seal()
      })
    }

    new PingPong

    assert(Await.result(done.future, 10.seconds))
  }

  override def afterAll() {
    system.shutdown()
  }
}
