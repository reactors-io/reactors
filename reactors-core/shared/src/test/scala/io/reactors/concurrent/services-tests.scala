package io.reactors
package concurrent



import io.reactors.test._
import java.io.InputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import org.apache.commons.io._
import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Failure



class ClockTest extends FunSuite with Matchers with BeforeAndAfterAll
with TimeLimitedTests {

  val system = ReactorSystem.default("TestSystem")

  def timeLimit = 10 seconds

  test("periodic timer should fire 3 times") {
    val done = Promise[Boolean]()
    system.spawn(Proto[PeriodReactor](done))
    assert(Await.result(done.future, 10.seconds))
  }

  test("timeout should fire exactly once") {
    val timeoutCount = Promise[Int]()
    system.spawn(Proto[TimeoutReactor](timeoutCount))
    Await.ready(timeoutCount.future, 10.seconds)
    assert(timeoutCount.future.value.get.get == 1,
      s"Total timeouts: ${timeoutCount.future.value}")
  }

  test("countdown should accumulate 45") {
    val total = Promise[Seq[Int]]()
    system.spawn(Proto[CountdownReactor](total))
    Await.ready(total.future, 10.seconds)
    assert(total.future.value.get.get == Seq(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
      s"Total sum of countdowns = ${total.future.value}")
  }

  override def afterAll() {
    system.shutdown()
  }

}


class PeriodReactor(val done: Promise[Boolean]) extends Reactor[Unit] {
  var countdown = 3
  system.clock.periodic(50.millis) on {
    countdown -= 1
    if (countdown <= 0) {
      main.seal()
      done.trySuccess(true)
    }
  }
}


class TimeoutReactor(val timeoutCount: Promise[Int]) extends Reactor[Unit] {
  var timeouts = 0
  system.clock.timeout(50.millis) on {
    timeouts += 1
    system.clock.timeout(500.millis) on {
      main.seal()
      timeoutCount.trySuccess(timeouts)
    }
  }
}


class CountdownReactor(val total: Promise[Seq[Int]]) extends Reactor[Unit] {
  val elems = mutable.Buffer[Int]()
  system.clock.countdown(10, 50.millis).onEventOrDone {
    x => elems += x
  } {
    main.seal()
    total.trySuccess(elems)
  }
}


class CustomServiceTest extends FunSuite with Matchers with BeforeAndAfterAll {
  val system = ReactorSystem.default("TestSystem")

  test("custom service should be retrieved") {
    val done = Promise[Boolean]()
    system.spawn(Proto[CustomServiceReactor](done))
    Await.ready(done.future, 10.seconds)
    assert(done.future.value.get.get, s"Status: ${done.future.value}")
  }

  override def afterAll() {
    system.shutdown()
  }
}


class CustomService(val system: ReactorSystem) extends Protocol.Service {
  val cell = RCell(0)

  def shutdown() {}
}


class CustomServiceReactor(val done: Promise[Boolean]) extends Reactor[Unit] {
  system.service[CustomService].cell := 1
  sysEvents onMatch {
    case ReactorStarted =>
      if (system.service[CustomService].cell() == 1) done.success(true)
      else done.success(false)
      main.seal()
  }
}


class ChannelsTest extends FunSuite with Matchers with BeforeAndAfterAll {
  val system = ReactorSystem.default("TestSystem")

  test("existing channel should be awaited") {
    val done = Promise[Boolean]()
    system.spawn(Reactor[Unit] { self =>
      val completer = system.channels.named("completer").open[String]
      completer.events onMatch {
        case "done" =>
          done.success(true)
          completer.seal()
      }
      system.channels.await[String]("test-reactor#completer").onEvent { ch =>
        ch ! "done"
        self.main.seal()
      }
    } withName("test-reactor"))
    assert(Await.result(done.future, 10.seconds))
  }

  test("non-existing channel should be awaited") {
    val done = Promise[Boolean]()
    system.spawn(Reactor[Unit] { self =>
      system.channels.await[String]("test-reactor#main").onEvent { ch =>
        ch ! "done"
        self.main.seal()
      }
    })
    Thread.sleep(1000)
    system.spawn(Reactor[String] { self =>
      self.main.events onMatch {
        case "done" =>
          done.success(true)
          self.main.seal()
      }
    } withName("test-reactor"))
    assert(Await.result(done.future, 10.seconds))
  }

  override def afterAll() {
    system.shutdown()
  }
}


object ChannelsCheck extends Properties("ChannelsCheck") with ExtendedProperties {

  val repetitions = 10
  val nameCounter = new AtomicLong(0L)

  property("channel should be awaited") =
    forAllNoShrink(detChoose(0, 50)) { n =>
      stackTraced {
        for (i <- 0 until repetitions) {
          val checkReactorName = "check-reactor-" + nameCounter.getAndIncrement()
          val system = ReactorSystem.default("check-system")
          val done = Promise[Boolean]()
          system.spawn(Reactor[Unit] { self =>
            system.channels.await[String](checkReactorName + "#main").onEvent { ch =>
              ch ! "done"
              self.main.seal()
            }
          })
          Thread.sleep(n)
          system.spawn(Reactor[String] { self =>
            self.main.events onMatch {
              case "done" =>
                done.success(true)
                self.main.seal()
            }
          } withName(checkReactorName))
          assert(Await.result(done.future, 10.seconds))
        }
        true
      }
    }

}
