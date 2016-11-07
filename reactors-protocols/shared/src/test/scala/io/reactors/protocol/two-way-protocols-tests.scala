package io.reactors
package protocol



import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._



class TwoWayProtocolsSpec extends AsyncFunSuite with AsyncTimeLimitedTests {
  val system = ReactorSystem.default("conversions")

  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("open two-way server, connect and send event") {
    val done = Promise[String]()
    val proto = Reactor[Unit] { self =>
      val server = system.channels.daemon.twoWayServer[Unit, String].serveTwoWay()

      server.connections.onEvent { twoWay =>
        twoWay.input onEvent { s =>
          done.success(s)
          self.main.seal()
        }
      }

      server.channel.connect() onEvent { twoWay =>
        twoWay.output ! "2-way"
      }
    }
    system.spawn(proto)
    done.future.map(s => assert(s == "2-way"))
  }

  test("open two-way server, connect, send event, await reply") {
    val done = Promise[Int]()
    val proto = Reactor[Unit] { self =>
      val server = system.channels.daemon.twoWayServer[Int, String].serveTwoWay()

      server.connections.onEvent { twoWay =>
        twoWay.input onEvent { s =>
          twoWay.output ! s.length
        }
      }

      server.channel.connect() onEvent { twoWay =>
        twoWay.output ! "how-long"
        twoWay.input onEvent { len =>
          done.success(len)
          self.main.seal()
        }
      }
    }
    system.spawn(proto)
    done.future.map(len => assert(len == 8))
  }

  test("open a two-way server and ping-pong-count up to 10") {
    val done = Promise[Int]
    val proto = Reactor[Unit] { self =>
      val server = system.channels.daemon.twoWayServer[Int, Int]serveTwoWay()

      server.connections.onEvent { twoWay =>
        twoWay.input onEvent { n =>
          twoWay.output ! n
        }
      }

      server.channel.connect() onEvent { twoWay =>
        twoWay.output ! 0
        var sum = 0
        twoWay.input onEvent { n =>
          sum += n
          if (n < 10) {
            twoWay.output ! n + 1
          } else {
            done.success(sum)
            self.main.seal()
          }
        }
      }
    }
    system.spawn(proto)
    done.future.map(n => assert(n == 55))
  }

  test("open a two-way server reactor and connect to it from another reactor") {
    val done = Promise[String]

    val server = system.twoWayServer[String, Int] { (server, twoWay) =>
      twoWay.input onEvent { n =>
        twoWay.output ! n.toString
      }
    }

    system.spawnLocal[Unit] { self =>
      server.connect() onEvent { twoWay =>
        twoWay.output ! 7
        twoWay.input onEvent { s =>
          done.success(s)
          self.main.seal()
        }
      }
    }

    done.future.map(s => assert(s == "7"))
  }
}
