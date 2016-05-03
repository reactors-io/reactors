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



class BalancerProtocolsSpec extends FunSuite {
  val system = ReactorSystem.default("balancer-protocols")

  test("default balancer with single channel") {
    val done = Promise[Boolean]()
    system.spawn(Reactor[Int] { self =>
      val bc = system.channels.daemon.balancer(Seq(self.main.channel))
      bc.channel ! 17
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
}


class BalancerProtocolsCheck
extends Properties("ServerProtocols") with ExtendedProperties {
  val system = ReactorSystem.default("check-system")

  val sizes = detChoose(0, 128)

  property("round-robin balancer") = forAllNoShrink(sizes) { num =>
    stackTraced {
      val ps = for (i <- 0 until num) yield Promise[Int]()
      val done = Promise[Boolean]()
      val chs = for (i <- 0 until num) yield system.spawn(Reactor[Int] { self =>
        self.main.events onEvent { x =>
          ps(i).success(x)
          self.main.seal()
        }
      })
      val balancer = system.spawn(Reactor[Int] { self =>
        val bc = system.channels.daemon.balancer(chs)
        for (i <- 0 until num) bc.channel ! i
        self.main.seal()
        done.success(true)
      })
      assert(Await.result(done.future, 10.seconds))
      for (i <- 0 until num) assert(Await.result(ps(i).future, 10.seconds) == i)
      true
    }
  }
}
