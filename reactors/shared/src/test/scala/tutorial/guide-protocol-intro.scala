/*!md
---
layout: tutorial
title: Introduction to Protocols
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/protocol-intro/index.html
pagenum: 1
pagetot: 40
section: guide-protocol
---
!*/
package tutorial



import org.scalatest._
import scala.collection._
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise



/*!md
## Protocols in the Reactor Model

The basic primitives of the reactor model - reactors, event streams and channels - allow
composing powerful communication abstractions.
In the following sections, we will go through some of the basic communication protocols
that the Reactors framework supports.
What these protocols have in common is
that they are not artificial extensions of the basic model.
Rather, they are composed from basic abstractions and simpler protocols.

In this section, we go through one of the simplest protocols,
namely the **server-client** protocol.
We first show how to implement a simple server-client protocol.
After that, we show how to use the standard server-client implementation
provided by the Reactors framework.
!*/
class GuideServerProtocol extends AsyncFunSuite {

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("custom server-client implementation") {
    val done = Promise[String]()
    def println(x: String) = done.success(x)

    /*!md
    ### Custom Server-Client Protocol

    Before we start, we import the contents of the `io.reactors`.
    We then create a default reactor system:
    !*/

    /*!begin-code!*/
    import io.reactors._

    val system = ReactorSystem.default("test-system")
    /*!end-code!*/

    /*!md
    ### Custom Server-Client Protocol

    Before we start, we import the contents of the `io.reactors`.
    We then create a default reactor system:
    !*/

    /*!begin-code!*/
    type Req[T, S] = (T, Channel[S])

    type Server[T, S] = Channel[Req[T, S]]

    def server[T, S](f: T => S): Server[T, S] = {
      val c = system.channels.open[Req[T, S]]
      c.events onMatch {
        case (x, reply) => reply ! f(x)
      }
      c.channel
    }
    /*!end-code!*/

    done.success("HELLO")
    done.future.map(t => assert(t == "HELLO"))
  }
}
