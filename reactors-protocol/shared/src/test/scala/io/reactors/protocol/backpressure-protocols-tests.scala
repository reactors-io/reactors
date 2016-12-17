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

  test("sliding backpressure channel") {
    val done = Promise[Seq[Int]]()
    val total = 256
    val maxBudget = 16
    val system = ReactorSystem.default("backpressure-protocols")
    val medium = Backpressure.Medium.default[Int]
    val policy = Backpressure.Policy.sliding(maxBudget)

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
          pump.buffer.available.is(true).once.on(consume())
        }
        consume()
      }
    }

    system.spawnLocal[Unit] { self =>
      backpressureServer.connectBackpressure[Int](medium, policy) onEvent { valve =>
        def produce(from: Int): Unit = {
          var i = from
          valve.available.is(true) on {
            while (valve.available() && i < total) {
              valve.channel ! i
              i += 1
            }
          }
        }
        produce(0)
      }
    }

    done.future.map(t => assert(t == (0 until total)))
  }

  test("batching backpressure channel") {
    val done = Promise[Seq[Int]]()
    val total = 256
    val maxBudget = 32
    val system = ReactorSystem.default("backpressure-protocols")
    val medium = Backpressure.Medium.default[Int]
    val policy = Backpressure.Policy.batching(maxBudget)

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
          pump.buffer.available.is(true).once.on(consume())
        }
        consume()
      }
    }

    system.spawnLocal[Unit] { self =>
      backpressureServer.connectBackpressure(medium, policy) onEvent { valve =>
        var i = 0
        valve.available.is(true) on {
          while (valve.available() && i < total) {
            valve.channel ! i
            i += 1
          }
        }
      }
    }

    done.future.map(t => assert(t == (0 until total)))
  }

  test("generic batching backpressure channel on reliable medium") {
    val done = Promise[Seq[Int]]()
    val total = 1024
    val maxBudget = 128
    val system = ReactorSystem.default("backpressure-protocols")
    val medium = Backpressure.Medium.reliable[Int](Reliable.TwoWay.Policy.reorder(128))
    val policy = Backpressure.Policy.batching(maxBudget)

    val server = system.backpressureConnectionServer(medium, policy) { s =>
      val seen = mutable.Buffer[Int]()
      s.connections onEvent { connection =>
        connection.buffer.available.is(true) on {
          while (connection.buffer.available()) {
            assert(connection.buffer.size < maxBudget)
            connection.pressure ! 1
            val x = connection.buffer.dequeue()
            seen += x
            if (x == (total - 1)) {
              done.success(seen)
              s.subscription.unsubscribe()
            }
          }
        }
      }
    }

    system.spawnLocal[Unit] { self =>
      server.connectBackpressure(medium, policy) onEvent { valve =>
        var i = 0
        valve.available.is(true) on {
          while (valve.available()) {
            valve.channel ! i
            i += 1
          }
        }
      }
    }

    done.future.map(t => assert(t == (0 until total)))
  }
}
