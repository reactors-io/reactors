package io.reactors
package protocol



import io.reactors.test._
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.collection._
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._



class BackpressureProtocolsSpec
extends AsyncFunSuite with AsyncTimeLimitedTests {
  val system = ReactorSystem.default("backpressure-protocols")

  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("open a per client backpressure channel and send events") {
    val done = Promise[Seq[String]]()
    val server = system.backpressurePerClient[String](3) { events =>
      val seen = mutable.Buffer[String]()
      events onEvent { s =>
        seen += s
        if (seen.length == 5) {
          done.success(seen)
          Reactor.self.main.seal()
        }
      }
    }
    system.spawnLocal[Long] { self =>
      server.link onEvent { link =>
        def traverse(i: Int) {
          if (i == 5) {
            self.main.seal()
          } else {
            if (link.trySend(i.toString)) traverse(i + 1)
            else link.available.once.on(traverse(i))
          }
        }
        traverse(0)
      }
    }
    done.future.map(xs => assert(xs == Seq("0", "1", "2", "3", "4")))
  }

  test("open backpressure for all channel and send events") {
    val done = Promise[Seq[String]]()
    val server = system.backpressureForAll[String](3) { events =>
      val seen = mutable.Buffer[String]()
      events onEvent { s =>
        seen += s
        if (seen.length == 15) {
          done.success(seen)
          Reactor.self.main.seal()
        }
      }
    }
    for (j <- 0 until 3) system.spawnLocal[Long] { self =>
      server.link onEvent { link =>
        def traverse(i: Int) {
          if (i == 5) {
            self.main.seal()
          } else {
            val x = j * 5 + i
            if (link.trySend(x.toString)) traverse(i + 1)
            else link.available.once.on(traverse(i))
          }
        }
        traverse(0)
      }
    }
    done.future.map { xs =>
      assert(xs.toSet == (0 until 15).map(_.toString).toSet)
    }
  }
}
