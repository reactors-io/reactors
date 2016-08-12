package io.reactors
package protocol



import io.reactors.test._
import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class PatternsSpec extends FunSuite {
  val system = ReactorSystem.default("server-protocols")

  test("throttle") {
    val a0 = Promise[Int]()
    val a1 = Promise[Int]()
    val a2 = Promise[Int]()
    val ch = system.spawn(Reactor[Int] { self =>
      var left = 3
      self.main.events.throttle(_ => 1000.millis) onEvent { x =>
        left -= 1
        if (left == 2) a2.success(x)
        if (left == 1) a1.success(x)
        if (left == 0) {
          a0.success(x)
          self.main.seal()
        }
      }
    })
    ch ! 7
    ch ! 11
    ch ! 17
    Thread.sleep(200)
    assert(a2.future.value.get.get == 7)
    assert(a1.future.value == None)
    assert(a0.future.value == None)
    Thread.sleep(1000)
    assert(a1.future.value.get.get == 11)
    assert(a0.future.value == None)
    Thread.sleep(1000)
    assert(a0.future.value.get.get == 17)
  }

  def retryTest(delays: Seq[Duration])(check: Seq[Duration] => Unit) {
    val done = Promise[Boolean]()
    val seen = Promise[Seq[Duration]]()
    val start = System.currentTimeMillis()
    val server = system.spawn(Reactor[(Unit, Channel[Unit])] { self =>
      val timestamps = mutable.Buffer[Duration]()
      self.main.events onMatch { case (_, ch) =>
        val current = System.currentTimeMillis()
        timestamps += (current - start).millis
        if (timestamps.length == delays.length) {
          seen.success(timestamps.toList)
          ch ! (())
          self.main.seal()
        }
      }
    })
    val ch = system.spawn(Reactor[Unit] { self =>
      var left = 3
      retry(delays)(server ? (())) onEvent { _ =>
        self.main.seal()
        done.success(true)
      }
    })
    assert(Await.result(done.future, 10.seconds) == true)
    val timestamps = seen.future.value.get.get
    check(timestamps)
  }

  test("retry regular") {
    retryTest(Backoff.regular(3, 500.millis)) { timestamps =>
      assert(timestamps(0) > 0.millis && timestamps(0) < 250.millis)
      assert(timestamps(1) > 450.millis && timestamps(1) < 750.millis)
      assert(timestamps(2) > 950.millis && timestamps(2) < 1250.millis)
    }
  }

  test("retry linear") {
    retryTest(Backoff.linear(3, 500.millis)) { timestamps =>
      assert(timestamps(0) >= 0.millis && timestamps(0) < 250.millis)
      assert(timestamps(1) >= 450.millis && timestamps(1) < 750.millis)
      assert(timestamps(2) >= 1450.millis && timestamps(2) < 1750.millis)
    }
  }

  test("retry exponential") {
    retryTest(Backoff.exp(4, 500.millis)) { timestamps =>
      assert(timestamps(0) >= 0.millis && timestamps(0) < 250.millis)
      assert(timestamps(1) >= 450.millis && timestamps(1) < 750.millis)
      assert(timestamps(2) >= 1450.millis && timestamps(2) < 1750.millis)
      assert(timestamps(3) >= 3450.millis && timestamps(3) < 3750.millis)
    }
  }

  test("retry random exponential") {
    retryTest(Backoff.exp(3, 500.millis)) { timestamps =>
      assert(timestamps(0) >= 0.millis && timestamps(0) < 250.millis)
      if (timestamps(1) >= 450.millis && timestamps(1) < 750.millis) {
        assert(timestamps(2) >= 1450.millis && timestamps(2) < 2750.millis)
      } else if (timestamps(1) >= 950.millis && timestamps(1) < 1250.millis) {
        assert(timestamps(2) >= 1950.millis && timestamps(2) < 3250.millis)
      } else {
        assert(false, timestamps)
      }
    }
  }
}


class PatternsCheck extends Properties("Patterns") with ExtendedProperties {
  val system = ReactorSystem.default("check-system")

  val sizes = detChoose(0, 50)

  property("throttle") = forAllNoShrink(sizes) {
    num =>
    stackTraced {
      val buffer = mutable.Buffer[Int]()
      val seen = Promise[Seq[Int]]()
      val ch = system.spawn(Reactor[Int] { self =>
        if (num == 0) seen.success(buffer)
        self.main.events.throttle(_ => 1.millis) onEvent { i =>
          buffer += i
          if (i == num - 1) {
            seen.success(buffer)
            self.main.seal()
          }
        }
      })
      for (i <- 0 until num) ch ! i
      try {
        assert(Await.result(seen.future, 10.seconds) == (0 until num))
      } catch {
        case t: Throwable =>
          println(num, buffer)
          throw t
      }
      true
    }
  }
}
