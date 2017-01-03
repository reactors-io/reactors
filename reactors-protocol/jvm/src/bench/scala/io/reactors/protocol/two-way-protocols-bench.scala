package io.reactors
package protocol



import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._
import org.scalameter.api._
import org.scalameter.japi.JBench



class TwoWayProtocolsBench extends JBench.OfflineReport {
  override def defaultConfig = Context(
    exec.minWarmupRuns -> 80,
    exec.maxWarmupRuns -> 160,
    exec.benchRuns -> 50,
    exec.independentSamples -> 3,
    verbose -> false
  )

  override def reporter = Reporter.Composite(
    new RegressionReporter(tester, historian),
    new MongoDbReporter[Double]
  )

  val sizes = Gen.range("size")(200000, 200000, 2000)

  @transient lazy val system = new ReactorSystem("reactor-bench")

  @gen("sizes")
  @benchmark("io.reactors.twoway")
  @curve("send")
  def send(sz: Int): Unit = {
    val done = Promise[Boolean]()
    val ch = system.spawnLocal[Int] { self =>
      var count = 0
      self.main.events onEvent { x =>
        count += 1
        if (count == sz) done.success(true)
      }
    }
    system.spawnLocal[Unit] { self =>
      var i = 0
      while (i < sz) {
        ch ! i
        i += 1
      }
    }
    assert(Await.result(done.future, 10.seconds))
  }

  @gen("sizes")
  @benchmark("io.reactors.twoway")
  @curve("two-way-send")
  def twoWaySend(sz: Int): Unit = {
    val done = Promise[Boolean]()
    val server = system.twoWayServer[Int, Int] { (server, link) =>
      var count = 0
      link.input onEvent { x =>
        count += 1
        if (count == sz) done.success(true)
      }
    }
    system.spawnLocal[Unit] { self =>
      server.connect() onEvent { link =>
        var i = 0
        while (i < sz) {
          link.output ! i
          i += 1
        }
      }
    }
    assert(Await.result(done.future, 10.seconds))
  }
}
