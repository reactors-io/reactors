/*!md
---
layout: tutorial
title: Two-Way Communication Protocol
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/two-way/index.html
pagenum: 3
pagetot: 40
section: guide-protocol
---
!*/
package tutorial



import org.scalatest._
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise



/*!md
## 2-Way Communication Protocol

In this section, we will inspect a 2-way communication protocol
that is a part of the `reactors-protocol` module.
!*/
class TwoWay extends AsyncFunSuite {

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("two-way") {
    val done = Promise[String]()
    def println(s: String) = done.success(s)

    /*!md
    TODO: Explain.
    !*/

    /*!begin-code!*/
    import io.reactors._
    import io.reactors.protocol._

    val system = ReactorSystem.default("test-system")
    /*!end-code!*/

    /*!md
    TODO: Explain.
    !*/

    /*!begin-code!*/
    val seeker = Reactor[Unit] { self =>
      val lengthServer = self.system.channels.twoWayServer[Int, String].serveTwoWay()

      lengthServer.connections.onEvent { twoWay =>
        twoWay.input.onEvent { s =>
          twoWay.output ! s.length
        }
      }
    /*!end-code!*/

    /*!md
    TODO: Explain.
    !*/

    /*!begin-code!*/
      lengthServer.channel.connect() onEvent { twoWay =>
        twoWay.output ! "What's my length?"
        twoWay.input onEvent { len =>
          if (len == 17) println("received correct reply")
          else println("reply incorrect: " + len)
        }
      }
    }

    system.spawn(seeker)
    /*!end-code!*/

    /*!md
    TODO: Explain.
    !*/

    done.future.map(s => assert(s == "received correct reply"))
  }

  /*!md
  TODO: Explain.
  !*/

  import io.reactors._
  import io.reactors.protocol._

  val system = ReactorSystem.default("test-system")

  test("two-way reactor server") {
    val done = Promise[Double]()
    def println(s: Double) = done.success(s)

    /*!begin-code!*/
    val seriesCalculator = Reactor.twoWayServer[Double, Int] {
      (server, twoWay) =>
      twoWay.input onEvent { n =>
        for (i <- 1 until n) {
          twoWay.output ! (1.0 / i)
        }
      }
    }
    val server = system.spawn(seriesCalculator)
    /*!end-code!*/

    /*!md
    TODO: Explain.
    !*/

    /*!begin-code!*/
    system.spawnLocal[Unit] { self =>
      server.connect() onEvent { twoWay =>
        twoWay.output ! 2
        twoWay.input onEvent { x =>
          println(x)
          twoWay.subscription.unsubscribe()
        }
      }
    }
    /*!end-code!*/

    done.future.map(t => assert(t == 1.0))
  }
}
