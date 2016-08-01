package io.reactors



import akka.actor._
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic._
import org.scalameter.api._
import org.scalameter.japi.JBench
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import scala.util.Failure



class ForkJoinThroughputBench extends JBench.OfflineReport {
  override def defaultConfig = Context(
    exec.minWarmupRuns -> 80,
    exec.maxWarmupRuns -> 120,
    exec.benchRuns -> 72,
    exec.independentSamples -> 1,
    verbose -> true
  )

  val sizes = Gen.range("size")(500, 2500, 500)

  @transient lazy val system = new ReactorSystem("reactor-bench")

  @gen("sizes")
  @benchmark("io.reactors.fork-join-throughput")
  @curve("onEvent")
  def reactorOnEvent(sz: Int) = {
    val done = new Array[Promise[Boolean]](ForkJoinThroughputBench.K)
    for (i <- 0 until ForkJoinThroughputBench.K) done(i) = Promise[Boolean]()

    val workers = (for (i <- 0 until ForkJoinThroughputBench.K) yield {
      system.spawn(Reactor[String] { self =>
        var count = 0
        self.main.events.on {
          count += 1
          if (count == sz) {
            done(i).success(true)
            self.main.seal()
          }
        }
      })
    }).toArray

    val producer = system.spawn(Reactor[String] { self =>
      var j = 0
      while (j < sz) {
        var i = 0
        while (i < ForkJoinThroughputBench.K) {
          workers(i) ! "event"
          i += 1
        }
        j += 1
      }
    })

    for (i <- 0 until ForkJoinThroughputBench.K) {
      Await.result(done(i).future, 10.seconds)
    }
  }

  var actorSystem: ActorSystem = _

  def akkaCountingActorSetup() {
    actorSystem = ActorSystem("actor-bench")
  }

  def akkaCountingActorTeardown() {
    actorSystem.shutdown()
  }

  // @gen("sizes")
  // @benchmark("io.reactors.fork-join-throughput")
  // @curve("akka")
  // @setupBeforeAll("akkaCountingActorSetup")
  // @teardownAfterAll("akkaCountingActorTeardown")
  // def akka(sz: Int) = {
  //   val done = Promise[Int]()
  //   assert(Await.result(done.future, 10.seconds) == sz)
  // }
}


object ForkJoinThroughputBench {
  val K = 32
}
