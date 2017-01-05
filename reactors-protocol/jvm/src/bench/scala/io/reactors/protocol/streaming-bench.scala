package io.reactors
package protocol



import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._
import org.scalameter.api._
import org.scalameter.japi.JBench



class StreamingBench extends JBench.OfflineReport {
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

  val maxSize = 200000
  val sizes = Gen.range("size")(maxSize, maxSize, 2000)

  @transient lazy val system = ReactorSystem.default("reactor-bench", """
    scheduler = {
      default = {
        budget = 8192
      }
    }
  """)

  @gen("sizes")
  @benchmark("io.reactors.protocol.streaming")
  @curve("grep")
  def grep(sz: Int): Unit = {
    import StreamingLibraryTest._
    val done = Promise[Boolean]()
    system.spawnLocal[Int] { self =>
      val source = new Source[String](system)
      val seen = mutable.Buffer[String]()
      val ready = source.filter(_.matches(".*keyword.*")).foreach(seen += _)
    }
    assert(Await.result(done.future, 10.seconds))
  }
}
