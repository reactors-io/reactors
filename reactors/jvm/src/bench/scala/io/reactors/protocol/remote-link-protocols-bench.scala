package io.reactors
package protocol



import java.util.concurrent.atomic.AtomicInteger
import org.scalameter.api._
import org.scalameter.japi.JBench
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class RemoteLinkProtocolsBench extends JBench.OfflineReport {
  override def defaultConfig = Context(
    exec.minWarmupRuns -> 80,
    exec.maxWarmupRuns -> 160,
    exec.benchRuns -> 50,
    exec.independentSamples -> 1,
    verbose -> true
  )

  override def reporter = Reporter.Composite(
    new RegressionReporter(tester, historian),
    new MongoDbReporter[Double]
  )

  override def persistor = Persistor.None

  val maxSize = 20000
  val sizes = Gen.range("size")(maxSize, maxSize, 2000)
  val delta = 1000
  val nameCounter = new AtomicInteger(0)

  val config = """
    remote = {
      default-schema = "udp"
    }
    scheduler = {
      default = {
        budget = 8192
      }
    }
  """
  @transient var sourceSystem: ReactorSystem = _
  @transient var targetSystem: ReactorSystem = _

  def beforeAll() {
    sourceSystem = ReactorSystem.default("bench-source-sys", config)
    targetSystem = ReactorSystem.default("bench-target-sys", config)
  }

  def afterAll() {
    sourceSystem.shutdown()
    targetSystem.shutdown()
  }

  @gen("sizes")
  @benchmark("io.reactors.protocol.link.remote")
  @curve("fire-and-forget")
  @setupBeforeAll("beforeAll")
  @teardownAfterAll("afterAll")
  def fireAndForget(sz: Int): Unit = {
    val started = Promise[Boolean]()
    val done = Promise[Boolean]()
    val receiverName = s"receiver-${nameCounter.incrementAndGet()}"
    val targetPort = targetSystem.remote.transport("udp").port
    val targetUrl = s"udp://localhost:$targetPort/$receiverName#main"
    val receiver = Reactor[Int] { self =>
      var count = 0
      self.main.events onEvent { x =>
        count += 1
        if (x > sz) {
          done.success(true)
          self.main.seal()
        }
      }
      started.success(true)
    }
    targetSystem.spawn(receiver.withName(receiverName))
    assert(Await.result(started.future, 10.seconds))
    sourceSystem.spawnLocal[Unit] { self =>
      val ch = self.system.remote.resolve[Int](targetUrl)
      var i = 0
      while (i < sz + delta) {
        ch ! i
        i += 1
      }
      self.main.seal()
    }
    assert(Await.result(done.future, 10.seconds))
  }

  @gen("sizes")
  @benchmark("io.reactors.protocol.link.remote")
  @curve("two-way-link")
  @setupBeforeAll("beforeAll")
  @teardownAfterAll("afterAll")
  def twoWaySend(sz: Int): Unit = {
    val started = Promise[Boolean]()
    val done = Promise[Boolean]()
    val receiverName = s"receiver-${nameCounter.incrementAndGet()}"
    val targetPort = targetSystem.remote.transport("udp").port
    val targetUrl = s"udp://localhost:$targetPort/$receiverName#main"
    val receiver = Reactor.twoWayServer[Int, Int] { server =>
      server.links onEvent { link =>
        var count = 0
        link.input onEvent { x =>
          count += 1
          if (x > sz) {
            done.trySuccess(true)
            server.subscription.unsubscribe()
          }
        }
      }
      started.success(true)
    }
    targetSystem.spawn(receiver.withName(receiverName))
    assert(Await.result(started.future, 10.seconds))
    sourceSystem.spawnLocal[Unit] { self =>
      val server = self.system.remote.resolve[TwoWay.Req[Int, Int]](targetUrl)
      server.connect() onEvent { link =>
        var i = 0
        while (i < sz + delta) {
          link.output ! i
          i += 1
        }
      }
    }
    assert(Await.result(done.future, 10.seconds))
  }

  @gen("sizes")
  @benchmark("io.reactors.protocol.link.remote")
  @curve("reliable-link")
  @setupBeforeAll("beforeAll")
  @teardownAfterAll("afterAll")
  def reliableSend(sz: Int): Unit = {
    val started = Promise[Boolean]()
    val done = Promise[Boolean]()
    val receiverName = s"receiver-${nameCounter.incrementAndGet()}"
    val targetPort = targetSystem.remote.transport("udp").port
    val targetUrl = s"udp://localhost:$targetPort/$receiverName#main"
    val policy = Reliable.Policy.reorder(100)
    val receiver = Reactor.reliableServer[Int](policy) { server =>
      server.links onEvent { link =>
        var count = 0
        link.events onEvent { x =>
          count += 1
          if (x > sz) {
            done.trySuccess(true)
            server.subscription.unsubscribe()
          }
        }
      }
      started.success(true)
    }
    targetSystem.spawn(receiver.withName(receiverName))
    assert(Await.result(started.future, 10.seconds))
    sourceSystem.spawnLocal[Unit] { self =>
      val server = self.system.remote.resolve[Reliable.Req[Int]](targetUrl)
      server.openReliable(policy) onEvent { r =>
        var i = 0
        while (i < sz + delta) {
          r.channel ! i
          i += 1
        }
      }
    }
    assert(Await.result(done.future, 10.seconds))
  }
}
