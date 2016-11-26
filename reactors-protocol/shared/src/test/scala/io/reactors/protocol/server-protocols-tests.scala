package io.reactors
package protocol



import io.reactors.common.afterTime
import io.reactors.test._
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._



class ServerProtocolsSpec
extends AsyncFunSuite with AsyncTimeLimitedTests {
  val system = ReactorSystem.default("server-protocols")

  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("request a reply from the server") {
    val p = Promise[Int]()
    val proto = Reactor[String] { self =>
      val server = system.channels.named("length-server").server[String, Int]
      server.events onEvent {
        case (s, ch) => ch ! s.length
      }
      self.sysEvents onMatch {
        case ReactorStarted =>
          (server.channel ? "ok") onEvent { len =>
            p.success(len)
            self.main.seal()
            server.seal()
          }
      }
    }
    system.spawn(proto)
    p.future.map(n => assert(n == 2))
  }

  test("request a reply from a server reactor") {
    val p = Promise[Int]()
    val server = system.server[Int, Int]((s, x) => x + 17)
    val client = system.spawn(Reactor[Int] { self =>
      (server ? 11) onEvent { y =>
        p.success(y)
      }
    })
    p.future.map(t => assert(t == 28))
  }

  test("request a reply from a maybe-server reactor") {
    val p = Promise[Int]()
    val failed = Promise[Boolean]()
    val server = system.maybeServer((x: Int) => x + 17, -1)
    val client = system.spawn(Reactor[Int] { self =>
      (server ? -18) on {
        failed.success(true)
      }
      system.clock.timeout(50.millis) on {
        (server ? 11) onEvent { y =>
          p.success(y)
          self.main.seal()
        }
      }
    })
    val proceed = Promise[Boolean]()
    p.future.onComplete { s =>
      assert(s.get == 28)
      afterTime(1000.millis) {
        proceed.success(true)
      }
    }
    for {
      _ <- proceed.future
    } yield {
      assert(failed.future.value == None)
    }
  }
}
