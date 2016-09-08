package io.reactors
package protocol



import io.reactors.common.IndexedSet
import io.reactors.test._
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class RouterProtocolsSpec extends FunSuite {
  val system = ReactorSystem.default("router-protocols")

  test("default router with single channel") {
    val done = Promise[Boolean]()
    system.spawn(Reactor[Int] { self =>
      val rc = system.channels.daemon.router[Int]
        .route(Router.roundRobin(Seq(self.main.channel)))
      rc.channel ! 17
      self.main.events onEvent { x =>
        if (x == 17) {
          done.success(true)
          self.main.seal()
        } else {
          done.success(false)
        }
      }
    })
    assert(Await.result(done.future, 10.seconds))
  }

  test("round robin should work correctly when targets change") {
    val done = Promise[Seq[Int]]()
    system.spawn(Reactor[Int] { self =>
      val seen = mutable.Buffer[Int]()
      val c1 = system.channels.open[Int]
      val c2 = system.channels.open[Int]
      val routees = new IndexedSet[Channel[Int]]
      routees += self.main.channel
      val rc = system.channels.daemon.router[Int].route(Router.roundRobin(routees))
      rc.channel ! 17
      self.main.events onEvent { x =>
        seen += x
        routees -= self.main.channel += c1.channel += c2.channel
        rc.channel ! 18
      }
      c1.events onEvent { x =>
        seen += x
        c1.seal()
        done.success(seen)
      }
      c2.events onEvent { x =>
        seen += x
        c2.seal()
        rc.channel ! 19
      }
    })
    assert(Await.result(done.future, 10.seconds) == Seq(17, 18, 19))
  }
}


class RouterProtocolsCheck
extends Properties("RouterProtocols") with ExtendedProperties {
  val system = ReactorSystem.default("check-system")

  val sizes = detChoose(0, 256)

  property("round-robin router") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val num = sz + 1
      val ps = for (i <- 0 until num) yield Promise[Int]()
      val done = Promise[Boolean]()
      val chs = for (i <- 0 until num) yield system.spawn(Reactor[Int] { self =>
        self.main.events onEvent { x =>
          ps(i).success(x)
          self.main.seal()
        }
      })
      system.spawn(Reactor[Int] { self =>
        val rc = system.channels.daemon.router[Int].route(Router.roundRobin(chs))
        for (i <- 0 until num) rc.channel ! i
        self.main.seal()
        done.success(true)
      })
      assert(Await.result(done.future, 10.seconds))
      for (i <- 0 until num) assert(Await.result(ps(i).future, 10.seconds) == i)
      true
    }
  }

  property("random router") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val num = sz + 1
      val done = Promise[Int]()
      val chs = for (i <- 0 until num) yield system.spawn(Reactor[Int] { self =>
        self.main.events onEvent { x =>
          if (x == 17) done.success(i)
          self.main.seal()
        }
        self.system.clock.timeout(5.seconds) on {
          self.main.seal()
        }
      })
      system.spawn(Reactor[Int] { self =>
        val rc = system.channels.daemon.router[Int].route(Router.random(chs))
        rc.channel ! 17
        self.main.seal()
      })
      val index = Await.result(done.future, 10.seconds)
      assert(index >= 0 && index < num, (index, num))
      true
    }
  }

  property("hash") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val num = sz + 1
      val done = for (i <- 0 until num) yield Promise[Boolean]()
      val chs = for (i <- 0 until num) yield system.spawn(Reactor[Int] { self =>
        self.main.events onEvent { x =>
          done(i).success(true)
          self.main.seal()
        }
        self.system.clock.timeout(5.seconds).on(self.main.seal())
      })
      system.spawn(Reactor[Int] { self =>
        val rc = system.channels.daemon.router[Int]
          .route(Router.hash(chs, (x: Int) => x))
        for (i <- 0 until num) rc.channel ! i
        self.main.seal()
      })
      for (i <- 0 until num) assert(Await.result(done(i).future, 4.seconds))
      true
    }
  }

  property("DRR") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val num = sz + 1
      val reps = 10
      val maxcost = 4
      var totalWork = new Array[Int](num)
      val latch = new CountDownLatch(num * reps)
      val chs = for (i <- 0 until num) yield system.spawn(Reactor[Int] { self =>
        self.main.events onEvent { x =>
          totalWork(i) += x
          latch.countDown()
        }
      })
      system.spawn(Reactor[Int] { self =>
        val rc = system.channels.daemon.router[Int].
          route(Router.deficitRoundRobin(chs, 1, (x: Int) => x))
        for (i <- 0 until num * reps) rc.channel ! (1 + i % maxcost)
      })
      latch.await(5, TimeUnit.SECONDS)
      assert(totalWork.max - totalWork.min <= maxcost, (totalWork.max, totalWork.min))
      true
    }
  }
}
