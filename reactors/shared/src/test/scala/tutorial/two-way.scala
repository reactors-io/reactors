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
In 2-way communication,
both the server and the client obtain a connection handle of type `TwoWay`,
which allows them to send and receive an unlimited number of events,
until they decide to close this connection.
!*/
class TwoWayProtocol extends AsyncFunSuite {

  implicit override def executionContext = ExecutionContext.Implicits.global

  type In = String

  type Out = Int

  /*!md
  The `TwoWay` type has two type parameters `I` and `O`,
  that describe the types of input and output events, respectively.
  The input events are the events that are incoming from the point of view of the
  owner of the `TwoWay` object.
  The output events are the outgoing events from the owner's point of view.
  Show graphically, this looks as follows:

  ```
   client-side  I <---------- I  server-side
                O ----------> O
  ```

  Note that these types are reversed depending on whether you are looking at the
  connection from the server-side or from the client-side.
  The type of the client-side 2-way connection is:
  !*/

  trait Foo {
    import io.reactors.protocol._

    /*!begin-code!*/
    val clientTwoWay: TwoWay[In, Out]
    /*!end-code!*/

    /*!md
    Whereas the type of the server would see the 2-way connection as:
    !*/

    /*!begin-code!*/
    val serverTwoWay: TwoWay[Out, In]
    /*!end-code!*/

    /*!md
    Accordingly, the `TwoWay` object contains an output channel `output`,
    and an input event stream `input`.
    To close the connection, the `TwoWay` object contains a subscription
    object called `subscription`, which frees the associated resources.
    !*/
  }

  test("two-way") {
    val done = Promise[String]()
    def println(s: String) = done.success(s)

    /*!md
    Let's start instantiate the 2-way channel protocol.
    We start by importing the contents of the `io.reactors`
    and the `io.reactors.protocol` packages,
    and then instantiating a default reactor system.
    !*/

    /*!begin-code!*/
    import io.reactors._
    import io.reactors.protocol._

    val system = ReactorSystem.default("test-system")
    /*!end-code!*/

    /*!md
    The 2-way communication protocol works in two phases.
    First, a client asks a 2-way connection server to establish a 2-way connection.
    Second, the client and the server use the 2-way channel to communicate.
    A single 2-way connection server can create many 2-way connections.

    As explained in an earlier section,
    there are usually several ways to instantiate the protocol - either as standalone
    reactor that runs only that protocol, or as a single protocol running inside a
    larger reactor.
    Let's start with a more general variant. We will declare a reactor,
    and instantiate a 2-way connection server within that reactor.
    The 2-way server will receive strings, and respond with the length of those strings.
    !*/

    /*!begin-code!*/
    val seeker = Reactor[Unit] { self =>
      val lengthServer = self.system.channels.twoWayServer[Int, String].serveTwoWay()
    /*!end-code!*/

    /*!md
    The above two lines declare a reactor `Proto` object,
    which instantiates a 2-way called `lengthServer`.
    This is done by first calling the `twoWayServer` method on the `channels` service,
    which is used to specify the input and the output type
    (from the point of view of the client),
    and then calling `serverTwoWay` to start the protocol.
    In our case, we set the input type `I` to `Int`, meaning that the client will
    receive integers from the server, and the output type `O` to `String`,
    meaning that the client will be sending strings to the server.

    The resulting object `lengthServer` represents the state of the connection.
    It has an event stream called `connections`, which emits an event every time
    some client requests a connection.
    If we do nothing with this event stream,
    the the server will be silent - it will start connections, but ignore events
    incoming from the client.
    How the client and server communicate over the 2-way channel
    (and when to terminate this communication) is up to the user to specify.
    To customize the 2-way communication protocol with our own logic,
    we need to react to the `TwoWay` events emitted by `connections`,
    and install callbacks to the `TwoWay` objects.

    In our case, for each incoming 2-way connection,
    we want to react to `input` strings by computing the length of the string
    and then sending that length back along the `output` channel.
    We can do this as follows:
    !*/

    /*!begin-code!*/
      lengthServer.connections.onEvent { twoWay =>
        twoWay.input.onEvent { s =>
          twoWay.output ! s.length
        }
      }
    /*!end-code!*/

    /*!md
    So far, so good - we have a working instance of the 2-way connection server.
    The current state of the reactor can be illustrated with the following figure,
    where our new channel appears alongside standard reactor channels:

    ```
    Channel[TwoWay.Req[Int, String]]
        |
        |       #-----------------------#
        \       |                       |
         \------o--> connections        |
                |                       |
                o--> main channel       |
                |                       |
                o--> sys channel        |
                |                       |
                #-----------------------#

    ```

    Note that the type of the 2-way server channel
    is `Channel[TwoWay.Req[Int, String]]`.
    If you would like to know what `TwoWay.Req[Int, String]]` type exactly is,
    you can study the implementation source code.
    However, if you only want to use the 2-way protocol,
    then understanding the implementation internals is not required,
    so we will skip that part.

    Next, let's start the client-side part of the protocol.
    The client must use the 2-way server channel to request a connection.
    The `lengthServer` object that we saw earlier has a field `channel`
    that must be used for this purpose.
    Generally, this channel must be known to the client
    (only the `channel` must be shared, not the complete `lengthServer` object).
    To make things simple, we will instantiate the client-side part of the protocol
    in the same reactor as the server-side part.
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
