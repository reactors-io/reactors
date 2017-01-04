package io.reactors
package protocol



import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._
import org.scalameter.api._
import org.scalameter.japi.JBench



class LinkProtocolsBench extends JBench.OfflineReport {
  override def defaultConfig = Context(
    exec.minWarmupRuns -> 80,
    exec.maxWarmupRuns -> 160,
    exec.benchRuns -> 5000,
    exec.independentSamples -> 1,
    verbose -> true
  )

  override def reporter = Reporter.Composite(
    new RegressionReporter(tester, historian),
    new MongoDbReporter[Double]
  )

  val maxSize = 200000
  val sizes = Gen.range("size")(maxSize, maxSize, 2000)

  @transient lazy val system = new ReactorSystem("reactor-bench")

  // @gen("sizes")
  // @benchmark("io.reactors.protocol.link")
  // @curve("send")
  // def send(sz: Int): Unit = {
  //   val done = Promise[Boolean]()
  //   val ch = system.spawnLocal[Int] { self =>
  //     var count = 0
  //     self.main.events onEvent { x =>
  //       count += 1
  //       if (count == sz) done.success(true)
  //     }
  //   }
  //   system.spawnLocal[Unit] { self =>
  //     var i = 0
  //     while (i < sz) {
  //       ch ! i
  //       i += 1
  //     }
  //   }
  //   assert(Await.result(done.future, 10.seconds))
  // }

  // @gen("sizes")
  // @benchmark("io.reactors.protocol.link")
  // @curve("two-way-link")
  // def twoWaySend(sz: Int): Unit = {
  //   val done = Promise[Boolean]()
  //   val server = system.twoWayServer[Int, Int] { (server, link) =>
  //     var count = 0
  //     link.input onEvent { x =>
  //       count += 1
  //       if (count == sz) done.success(true)
  //     }
  //   }
  //   system.spawnLocal[Unit] { self =>
  //     server.connect() onEvent { link =>
  //       var i = 0
  //       while (i < sz) {
  //         link.output ! i
  //         i += 1
  //       }
  //     }
  //   }
  //   assert(Await.result(done.future, 10.seconds))
  // }

  // @gen("sizes")
  // @benchmark("io.reactors.protocol.link")
  // @curve("reliable-link")
  // def reliableSend(sz: Int): Unit = {
  //   val done = Promise[Boolean]()
  //   val policy = Reliable.Policy.reorder(8192)
  //   val server = system.reliableServer[String](policy) {
  //     (server, link) =>
  //     var count = 0
  //     link.events onEvent { x =>
  //       count += 1
  //       if (count == sz) done.success(true)
  //     }
  //   }
  //   system.spawnLocal[Unit] { self =>
  //     server.openReliable(policy) onEvent { r =>
  //       var i = 0
  //       while (i < sz) {
  //         r.channel ! "data"
  //         i += 1
  //       }
  //     }
  //   }
  //   assert(Await.result(done.future, 10.seconds))
  // }

  // @gen("sizes")
  // @benchmark("io.reactors.protocol.link")
  // @curve("reliable-optimized-link")
  // def reliableOptimizedSend(sz: Int): Unit = {
  //   val done = Promise[Boolean]()
  //   val policy = Reliable.Policy.fastReorder(8192)
  //   val server = system.reliableServer[String](policy) {
  //     (server, link) =>
  //     var count = 0
  //     link.events onEvent { x =>
  //       count += 1
  //       if (count == sz) done.success(true)
  //     }
  //   }
  //   system.spawnLocal[Unit] { self =>
  //     server.openReliable(policy) onEvent { r =>
  //       var i = 0
  //       while (i < sz) {
  //         r.channel ! "data"
  //         i += 1
  //       }
  //     }
  //   }
  //   assert(Await.result(done.future, 10.seconds))
  // }

  @gen("sizes")
  @benchmark("io.reactors.protocol.link")
  @curve("backpressure-simple-link")
  def backpressureSimpleSend(sz: Int): Unit = {
    val done = Promise[Boolean]()
    val medium = Backpressure.Medium.default[Int]
    val policy = Backpressure.Policy.batching(8192)
    val server = system.backpressureServer(medium, policy) {
      case Backpressure.PumpServer(ch, links, sub) =>
        links onEvent { pump =>
          var count = 0
          pump.available.is(true) on {
            while (pump.available()) {
              pump.dequeue()
              count += 1
              if (count == sz) done.success(true)
            }
          }
        }
    }
    system.spawnLocal[Unit] { self =>
      server.openBackpressure(medium, policy) onEvent { valve =>
        var i = 0
        valve.available.is(true) on {
          while (valve.available() && i < sz) {
            valve.channel ! 0
            i += 1
          }
        }
      }
    }
    assert(Await.result(done.future, 10.seconds))
  }
}
