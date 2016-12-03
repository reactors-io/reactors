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

  test("open a simple sliding backpressure channel and send events") {
    val done = Promise[Seq[Int]]()
    val total = 256
    val maxBudget = 16
    val system = ReactorSystem.default("backpressure-protocols")
    val medium = Backpressure.Medium.default[Int]
    val policy = Backpressure.Policy.sliding[Int](maxBudget)

    val backpressureServer = system.backpressureServer(medium, policy) { pumpServer =>
      pumpServer.connections onEvent { pump =>
        val seen = mutable.Buffer[Int]()
        def consume(): Unit = {
          while (pump.buffer.available()) {
            assert(pump.buffer.size <= maxBudget)
            val x = pump.buffer.dequeue()
            seen += x
            if (x == total - 1) {
              done.success(seen)
              pumpServer.subscription.unsubscribe()
            }
          }
          pump.buffer.available.becomes(true).once.on(consume())
        }
        consume()
      }
    }

    system.spawnLocal[Unit] { self =>
      backpressureServer.connectBackpressure(medium, policy) onEvent { valve =>
        def produce(from: Int): Unit = {
          var i = from
          while (valve.available() && i < total) {
            valve.channel ! i
            i += 1
          }
          valve.available.becomes(true).once.on(produce(i))
        }
        produce(0)
      }
    }

    done.future.map(t => assert(t == (0 until total)))
  }

  test("open a simple batching backpressure channel and send events") {
    val done = Promise[Seq[Int]]()
    val total = 256
    val maxBudget = 32
    val system = ReactorSystem.default("backpressure-protocols")
    val medium = Backpressure.Medium.default[Int]
    val policy = Backpressure.Policy.batching[Int](maxBudget)

    val backpressureServer = system.backpressureServer(medium, policy) { pumpServer =>
      pumpServer.connections onEvent { pump =>
        val seen = mutable.Buffer[Int]()
        def consume(): Unit = {
          while (pump.buffer.available()) {
            assert(pump.buffer.size < maxBudget)
            val x = pump.buffer.dequeue()
            seen += x
            if (x == (total - 1)) {
              done.success(seen)
              pumpServer.subscription.unsubscribe()
            }
          }
          pump.buffer.available.becomes(true).once.on(consume())
        }
        consume()
      }
    }

    system.spawnLocal[Unit] { self =>
      backpressureServer.connectBackpressure(medium, policy) onEvent { valve =>
        def produce(from: Int): Unit = {
          var i = from
          while (valve.available() && i < total) {
            valve.channel ! i
            i += 1
          }
          valve.available.becomes(true).once.on(produce(i))
        }
        produce(0)
      }
    }

    done.future.map(t => assert(t == (0 until total)))
  }
}
