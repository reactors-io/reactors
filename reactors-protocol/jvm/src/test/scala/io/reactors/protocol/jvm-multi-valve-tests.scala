package io.reactors.protocol



import io.reactors.Channel
import io.reactors.RCell
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
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random



class MultiValveCheck extends Properties("MultiValve") with ExtendedProperties {
  val sizes = detChoose(1, 1024)
  val windows = detChoose(1, 256)
  val probs = detChoose(0, 100)

  property("available when empty") = forAllNoShrink(sizes, windows) {
    (total, window) =>
    stackTraced {
      val system = ReactorSystem.default("test")
      try {
        val done = Promise[Boolean]()

        system.spawnLocal[Unit] { self =>
          val multi = new MultiValve[Int](window)

          var i = 0
          multi.out.available.is(true) on {
            while (multi.out.available() && i < total) {
              multi.out.channel ! i
              i += 1
            }
            if (i == total) done.trySuccess(true)
          }
        }

        Await.result(done.future, 10.seconds)
      } finally {
        system.shutdown()
      }
    }
  }

  property("single valve, always available") = forAllNoShrink(sizes, windows) {
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

          var i = 0
          multi.out.available.is(true) on {
            while (multi.out.available() && i < total) {
              multi.out.channel ! i
              i += 1
            }
            if (i == total) done.trySuccess(true)
          }
        }

        Await.result(done.future, 10.seconds)
      } finally {
        system.shutdown()
      }
    }
  }

  property("two valves, always available") = forAllNoShrink(sizes, windows) {
    (total, window) =>
    stackTraced {
      val system = ReactorSystem.default("test")
      try {
        val done = Promise[Boolean]()

        system.spawnLocal[Unit] { self =>
          val vs = for (i <- 0 until 2) yield Valve(
            new Channel.Zero[Int],
            new Signal.Const(true),
            Subscription.empty
          )
          val multi = new MultiValve[Int](window)
          for (v <- vs) multi += v

          var i = 0
          multi.out.available.is(true) on {
            while (multi.out.available() && i < total) {
              multi.out.channel ! i
              i += 1
            }
            if (i == total) done.trySuccess(true)
          }
        }

        Await.result(done.future, 10.seconds)
      } finally {
        system.shutdown()
      }
    }
  }

  property("single valve, sometimes temporarily unavailable") =
    forAllNoShrink(sizes, windows, windows) { (total, window, valveWindow) =>
      stackTraced {
        val system = ReactorSystem.default("test")

        try {
          val done = Promise[Seq[Int]]()

          system.spawnLocal[Unit] { self =>
            // Single consumer, with backpressure.
            val valveTokens = RCell(valveWindow)
            val seen = mutable.Buffer[Int]()
            val backpressure = system.channels.open[Int]
            backpressure.events onEvent { n =>
              valveTokens := valveTokens() + n
            }
            val internal = system.channels.open[Int]
            internal.events onEvent { x =>
              seen += x
              if (valveTokens() == 0) {
                backpressure.channel ! valveWindow
              }
              if (x == total - 1) {
                done.success(seen)
              }
            }
            val c = system.channels.shortcut.open[Int]
            c.events onEvent { x =>
              valveTokens := valveTokens() - 1
              internal.channel ! x
            }
            val valve = Valve(
              c.channel,
              valveTokens.map(_ > 0).changed(true).toSignal(true),
              Subscription.empty
            )
            val multi = new MultiValve[Int](window)
            multi += valve

            // Producer.
            var i = 0
            multi.out.available.is(true) on {
              while (multi.out.available() && i < total) {
                multi.out.channel ! i
                i += 1
              }
            }
          }

          Await.result(done.future, 10.seconds) == (0 until total)
        } finally {
          system.shutdown()
        }
      }
    }

  property("single valve, using backpressure channels") =
    forAllNoShrink(sizes, windows, windows) { (total, window, pressureWindow) =>
      stackTraced {
        val system = ReactorSystem.default("test")

        try {
          val done = Promise[Seq[Int]]()

          system.spawnLocal[Unit] { self =>
            val seen = mutable.Buffer[Int]()
            val medium = Backpressure.Medium.default[Int]
            val policy = Backpressure.Policy.batching(pressureWindow)
            val server = system.channels.backpressureServer(medium)
              .serveBackpressure(medium, policy)
            server.connections onEvent { c =>
              c.buffer.available.is(true) on {
                while (c.buffer.available()) {
                  val x = c.buffer.dequeue()
                  seen += x
                  if (x == (total - 1)) {
                    done.success(seen)
                  }
                }
              }
            }

            server.channel.connectBackpressure(medium, policy) onEvent { v =>
              val multi = new MultiValve[Int](window)
              multi += v

              var i = 0
              multi.out.available.is(true) on {
                while (multi.out.available() && i < total) {
                  multi.out.channel ! i
                  i += 1
                }
              }
            }
          }

          Await.result(done.future, 10.seconds) == (0 until total)
        } finally {
          system.shutdown()
        }
      }
    }

  property("two valves, using backpressure") =
    forAllNoShrink(sizes, windows, windows) { (total, window, pressureWindow) =>
      stackTraced {
        val system = ReactorSystem.default("test")

        try {
          val done1 = Promise[Boolean]
          val done2 = Promise[Boolean]

          system.spawnLocal[Unit] { self =>
            val medium = Backpressure.Medium.default[Int]
            val policy = Backpressure.Policy.batching(pressureWindow)

            val server = system.channels.backpressureServer(medium)
              .serveBackpressure(medium, policy)
            def accept(p: Pump[Int], d: Promise[Boolean]): Unit = {
              val seen = mutable.Buffer[Int]()
              p.buffer.available.is(true) onEvent { x =>
                while (p.buffer.available()) {
                  seen += p.buffer.dequeue()
                }
                if (seen.size == total) d.success(seen == (0 until total))
              }
            }
            server.connections.once onEvent { p => accept(p, done1) }
            server.connections.drop(1).once onEvent { p => accept(p, done2) }

            server.channel.connectBackpressure(medium, policy) onEvent { v1 =>
              server.channel.connectBackpressure(medium, policy) onEvent { v2 =>
                val multi = new MultiValve[Int](window)
                multi += v1
                multi += v2

                var i = 0
                multi.out.available.is(true) on {
                  while (multi.out.available() && i < total) {
                    multi.out.channel ! i
                    i += 1
                  }
                }
              }
            }
          }

          val done = for {
            x <- done1.future
            y <- done2.future
          } yield x && y
          Await.result(done, 10.seconds)
        } finally {
          system.shutdown()
        }
      }
    }
}
