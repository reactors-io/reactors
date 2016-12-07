package io.reactors.protocol



import io.reactors.Channel
import io.reactors.ReactorSystem
import io.reactors.Signal
import io.reactors.Subscription
import io.reactors.test._
import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import scala.collection._
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.util.Random



class MultiValveCheck extends Properties("MultiValve") with ExtendedProperties {
  val sizes = detChoose(1, 1000)
  val smallSizes = detChoose(1, 64)
  val probs = detChoose(0, 100)

  property("available when empty") = forAllNoShrink(sizes, smallSizes) {
    (total, window) =>
    stackTraced {
      val system = ReactorSystem.default("test")
      try {
        val done = Promise[Boolean]()

        system.spawnLocal[Unit] { self =>
          val multi = new MultiValve[Int](window)
          for (i <- 0 until total) {
            assert(multi.out.available())
            multi.out.channel ! i
          }
          multi.out.available()
          done.success(true)
        }

        Await.result(done.future, 5.seconds)
      } finally {
        system.shutdown()
      }
    }
  }

  property("single valve, always available") = forAllNoShrink(sizes, smallSizes) {
    (total, window) =>
    stackTraced {
      val system = ReactorSystem.default("test")
      try {
        val done = Promise[Boolean]()

        system.spawnLocal[Unit] { self =>
          val valve = Valve(
            new Channel.Zero[Int],
            new Signal.Const(true),
            Subscription.empty
          )
          val multi = new MultiValve[Int](window)
          multi += valve

          for (i <- 0 until total) {
            assert(multi.out.available())
            multi.out.channel ! i
          }
          multi.out.available()
          done.success(true)
        }

        Await.result(done.future, 5.seconds)
      } finally {
        system.shutdown()
      }
    }
  }
}
