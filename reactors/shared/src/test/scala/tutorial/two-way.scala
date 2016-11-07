/*!md
---
layout: tutorial
title: Two-Way Communication Protocol
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/two-way/index.html
pagenum: 2
pagetot: 40
section: guide-protocol
---
!*/
package tutorial



import org.scalatest._
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise



/*!md
## The Hello World Program

This section contains a simple, working Hello World program.
We won't get into too much details - you can find deeper information
in the subsequent sections.
We will define a reactor that waits for one incoming event,
prints a message to the standard output,
and then terminates.
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

}
