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

  def optimizedReorder(window: Int) = new Reliable.Policy {
    val pool = new java.util.concurrent.ArrayBlockingQueue[Stamp.Some[AnyRef]](8192)

    def alloc[T](x: T, stamp: Long): Stamp.Some[T] = {
      if (!pool.isEmpty) {
        val s = pool.poll().asInstanceOf[Stamp.Some[T]]
        if (s != null) {
          s.x = x
          s.stamp = stamp
          return s
        }
      }
      new Stamp.Some(x, stamp)
    }

    def dealloc[T](s: Stamp.Some[T]) = {
      pool.offer(s.asInstanceOf[Stamp.Some[AnyRef]])
    }

    def client[T: Arrayable](
      sends: Events[T],
      twoWay: io.reactors.protocol.TwoWay[Long, Stamp[T]]
    ): Subscription = {
      var lastStamp = 0L
      val io.reactors.protocol.TwoWay(channel, acks, subscription) = twoWay
      sends onEvent { x =>
        lastStamp += 1
        channel ! alloc(x, lastStamp)
      }
    }

    def server[T: Arrayable](
      twoWay: io.reactors.protocol.TwoWay[Stamp[T], Long],
      deliver: Channel[T]
    ): Subscription = {
      val io.reactors.protocol.TwoWay(acks, events, subscription) = twoWay
      var nextStamp = 1L
      val queue = new io.reactors.common.BinaryHeap[Stamp[T]]()(
        implicitly,
        Order((x, y) => (x.stamp - y.stamp).toInt)
      )
      val ackSub = Reactor.self.sysEvents onMatch {
        case ReactorPreempted => acks ! nextStamp
      }
      events onMatch {
        case stamp @ Stamp.Some(x, timestamp) =>
          if (timestamp == nextStamp) {
            nextStamp += 1
            deliver ! x
            dealloc(stamp)
            while (queue.nonEmpty && queue.head.stamp == nextStamp) {
              val Stamp.Some(y, _) = queue.dequeue()
              nextStamp += 1
              deliver ! y
              dealloc(stamp)
            }
          } else {
            queue.enqueue(stamp)
          }
      } chain (ackSub) andThen (acks ! -1)
    }
  }

  lazy val optimizedPolicy = optimizedReorder(8192)

  @gen("sizes")
  @benchmark("io.reactors.protocol.link")
  @curve("reliable-optimized-link")
  def reliableOptimizedSend(sz: Int): Unit = {
    val done = Promise[Boolean]()
    val policy = optimizedPolicy
    val server = system.reliableServer[String](policy) {
      (server, link) =>
      var count = 0
      link.events onEvent { x =>
        count += 1
        if (count == sz) done.success(true)
      }
    }
    system.spawnLocal[Unit] { self =>
      server.openReliable(policy) onEvent { r =>
        var i = 0
        while (i < sz) {
          r.channel ! "data"
          i += 1
        }
      }
    }
    assert(Await.result(done.future, 10.seconds))
  }
}
