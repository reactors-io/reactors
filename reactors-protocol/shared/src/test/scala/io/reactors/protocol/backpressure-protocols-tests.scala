package io.reactors
package protocol



import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration._



class BackpressureProtocolsSpec
extends AsyncFunSuite with AsyncTimeLimitedTests {
  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("open a simple backpressure channel and send events") {
    val done = Promise[Seq[Int]]()
    val system = ReactorSystem.default("backpressure-protocols")
    val medium = Backpressure.Medium.default[Int]
    val policy = Backpressure.Policy.sliding[Int](16)
    val total = 256

    val backpressureServer = system.backpressureServer(medium, policy) { pumpServer =>
      pumpServer.connections onEvent { pump =>
        val seen = mutable.Buffer[Int]()
        def consume(): Unit = {
          while (pump.buffer.available()) {
            val x = pump.buffer.dequeue()
            seen += x
            if (x == total - 1) {
              done.success(seen)
              pumpServer.subscription.unsubscribe()
            }
          }
          pump.buffer.available.onceTrue.on(consume())
        }
        consume()
      }
    }

    val client = system.spawnLocal[Int] { self =>
      backpressureServer.connectBackpressure(medium, policy) onEvent { valve =>
        def produce(start: Int): Unit = {
          var i = start
          while (valve.available() && i < total) {
            valve.channel ! i
            i += 1
          }
          valve.available.onceTrue.on(produce(i))
        }
        produce(0)
      }
    }

    done.future.map(t => assert(t == (0 until total)))
  }
}
