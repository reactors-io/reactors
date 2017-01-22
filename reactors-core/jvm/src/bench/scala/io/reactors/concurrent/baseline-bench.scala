package io.reactors
package concurrent



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



class BaselineBench extends JBench.OfflineReport {
  override def defaultConfig = Context(
    exec.minWarmupRuns -> 80,
    exec.maxWarmupRuns -> 120,
    exec.benchRuns -> 36,
    exec.independentSamples -> 1,
    verbose -> true
  )

  override def reporter = Reporter.Composite(
    new RegressionReporter(tester, historian),
    new MongoDbReporter[Double]
  )

  val sizes = Gen.range("size")(25000, 25000, 5000)

  @transient lazy val system = new ReactorSystem("reactor-bench")

  @gen("sizes")
  @benchmark("io.reactors.baseline")
  @curve("onEvent")
  def reactorOnEventBaseline(sz: Int) = {
    val done = Promise[Boolean]()

    system.spawn(Reactor[String] { self =>
      var left = sz
      self.sysEvents onMatch {
        case ReactorPreempted =>
          self.main.channel ! "more"
      }
      self.main.events onEvent { x =>
        left -= 1
        if (left == 0) {
          done.success(true)
          self.main.seal()
        }
      }
    })

    assert(Await.result(done.future, 100.seconds))
  }
}
