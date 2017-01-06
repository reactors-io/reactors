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
    exec.jvmflags -> List(
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
    ),
    verbose -> true
  )

  override def reporter = Reporter.Composite(
    new RegressionReporter(tester, historian),
    new MongoDbReporter[Double]
  )

  override def persistor = Persistor.None

  val maxSize = 20000
  val sizes = Gen.range("size")(maxSize, maxSize, 2000)
  val parts = Array(
    "car", "ary", "plen", "bin", "ation", "innov", "add", "batch", "tern", "ition",
    "observ", "adher", "admir", "comput", "fru", "exponenti", "tray", "suspend",
    "agent", "exec", "sampl", "warm", "maximiz", "minimiz", "transport"
  )
  val dictionary = for {
    a <- parts
    b <- parts
  } yield a + b

  @transient lazy val system = ReactorSystem.default("reactor-bench", """
    scheduler = {
      default = {
        budget = 8192
      }
    }
  """)

  // @gen("sizes")
  // @benchmark("io.reactors.protocol.streaming")
  // @curve("grep")
  // def grep(sz: Int): Unit = {
  //   import StreamingLibraryTest._
  //   val done = Promise[Boolean]()
  //   system.spawnLocal[Int] { self =>
  //     val source = new Source[String](system)
  //     val seen = mutable.Buffer[String]()
  //     source.filter(_.matches(".*(keyword|done).*")).foreach { x =>
  //       seen += x
  //       if (x == "done") {
  //         done.success(true)
  //         self.main.seal()
  //       }
  //     } on {
  //       var i = 0
  //       source.valve.available.is(true) on {
  //         while (source.valve.available() && i <= sz) {
  //           if (i == sz) source.valve.channel ! "done"
  //           else source.valve.channel ! ("hm" * (i % 10) + "-keyword-" + i)
  //           i += 1
  //         }
  //       }
  //     }
  //   }
  //   assert(Await.result(done.future, 10.seconds))
  // }

  // @gen("sizes")
  // @benchmark("io.reactors.protocol.streaming")
  // @curve("word-count")
  // def wordCount(sz: Int): Unit = {
  //   import StreamingLibraryTest._
  //   val done = Promise[Boolean]()
  //   system.spawnLocal[Int] { self =>
  //     val source = new Source[String](system)
  //     var count = 0
  //     def addWord(histogram: immutable.Map[String, Int], word: String) = {
  //       histogram.get(word) match {
  //         case Some(count) => histogram + ((word, count + 1))
  //         case None => histogram + ((word, 1))
  //       }
  //     }
  //     source.scanPast(immutable.Map[String, Int]())(addWord).foreach { histogram =>
  //       if (histogram.contains(dictionary(0))) {
  //         count = histogram(dictionary(0))
  //       }
  //       if (count > sz / dictionary.length - 2) {
  //         done.trySuccess(true)
  //         self.main.seal()
  //       }
  //     } on {
  //       var i = 0
  //       source.valve.available.is(true) on {
  //         while (source.valve.available() && i <= sz) {
  //           if (i == sz) source.valve.channel ! "done"
  //           else source.valve.channel ! dictionary(i % dictionary.size)
  //           i += 1
  //         }
  //       }
  //     }
  //   }
  //   assert(Await.result(done.future, 10.seconds))
  // }

  @gen("sizes")
  @benchmark("io.reactors.protocol.streaming")
  @curve("top-k")
  def topK(sz: Int): Unit = {
    import StreamingLibraryTest._
    val done = Promise[Boolean]()
    var topRetweet = ""
    system.spawnLocal[Int] { self =>
      val source = new Source[(String, Int)](system)
      var count = 0
      source.sliding(20).map { tweets =>
        var maxRetweets = 0
        var maxTweet = ""
        tweets.foreach { case (tweet, numRetweets) =>
          if (numRetweets > maxRetweets) {
            maxRetweets = numRetweets
            maxTweet = tweet
          }
        }
        maxTweet
      } foreach { tweet =>
        topRetweet = tweet
        if (tweet == "done") {
          done.trySuccess(true)
          self.main.seal()
        }
      } on {
        var i = 0
        source.valve.available.is(true) on {
          while (source.valve.available() && i <= sz) {
            if (i == sz) source.valve.channel ! ("done", 600)
            else source.valve.channel ! (dictionary(i % dictionary.size), i % 40)
            i += 1
          }
        }
      }
    }
    assert(Await.result(done.future, 10.seconds))
  }
}
