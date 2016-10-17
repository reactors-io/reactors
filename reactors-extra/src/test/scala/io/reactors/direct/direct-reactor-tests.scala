package io.reactors
package direct



import org.coroutines._
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class DirectReactorTest extends FunSuite with Matchers with BeforeAndAfterAll {
  val system = ReactorSystem.default("test-system")

  test("reactor defined with direct") {
    val proto = Reactor.direct[String] {
      val self = Reactor.self[String]
      val x = receive(self.main.events)
    }
  }

  test("reactor terminate after first message") {
    val done = Promise[Boolean]()
    val ch = system.spawn(Reactor.direct[String] {
      val self = Reactor.self[String]
      val x = receive(self.main.events)
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
    val ch = system.spawn(Reactor.direct[String] {
      val self = Reactor.self[String]
      val seen = mutable.Buffer[String]()
      var left = n
      while (left > 0) {
        seen += receive(self.main.events)
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
      val ping: Channel[String] = system.spawn(Reactor.direct[String] {
        val self = Reactor.self[String]
        val pong = system.spawn(Reactor.direct[String] {
          val self = Reactor.self[String]
          var left = n
          while (left > 0) {
            val x = receive(self.main.events)
            ping ! "pong"
            left -= 1
          }
          self.main.seal()
        })
        val start = receive(self.main.events)
        assert(start == "start")
        var left = n
        while (left > 0) {
          pong ! "ping"
          val x = receive(self.main.events)
          left -= 1
        }
        done.success(true)
        self.main.seal()
      })
      ping ! "start"
    }
    new PingPong

    assert(Await.result(done.future, 10.seconds))
  }

  override def afterAll() {
    system.shutdown()
  }
}
