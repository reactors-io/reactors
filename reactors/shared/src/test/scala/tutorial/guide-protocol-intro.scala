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
We first show how to implement a simple server-client protocol ourselves.
After that, we show how to use the standard server-client implementation
provided by the Reactors framework.
Note that, in the later sections on protocols,
we will not dive into the implementation,
but instead immediately show how to use the already implemented protocol
provided by the framework.

This approach will server several purposes.
First, you should get an idea of how to implement a communication pattern
using event streams and channels.
Second, you should get a feel for the fact that there is more than one way
to implement a protocol and expose it to clients.
Finally, you will see how protocols are structured in the Reactors framework.
!*/
class GuideServerProtocol extends AsyncFunSuite {

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("custom server-client implementation") {
    val done = Promise[String]()
    def println(x: String) = done.success(x)

    /*!md
    ### Custom Server-Client Protocol

    Before we start, we import the contents of the `io.reactors` package.
    We then create a default reactor system:
    !*/

    /*!begin-code!*/
    import io.reactors._

    val system = ReactorSystem.default("test-system")
    /*!end-code!*/

    /*!md
    Let's now consider take a look at the server-client protocol more closely.
    The protocol proceeds as follows: first, the client sends a request value to the
    server. Then, the server uses the request to compute a response value and send
    it to the client. But to do that, the server needs a response channel to send the
    value along. This means that the client must not only send the request value to
    the server, but also send it a channel used for the reply.
    The request sent by the client is thus a tuple with a value and the reply channel.
    The channel used by the server must accept such tuples.
    We capture these relations with the following two types:
    !*/

    /*!begin-code!*/
    type Req[T, S] = (T, Channel[S])

    type Server[T, S] = Channel[Req[T, S]]
    /*!end-code!*/

    /*!md
    Here, the `T` is type of the request value,
    and `S` is the type of the response value.
    The `Req` type represents the request - a tuple of the request value `T` and
    the reply channel for responses `S`.
    The `Server` type is then just a channel that accepts request objects.

    Question poses itself then - how can we create a `Server` channel?
    There are several requirements that a factory method for the `Server` channel
    should satisfy.
    First, it should be generic in the input and the output type.
    Second, it should be generic in how the input type is mapped to the output type.
    Third, when a request is sent to the server, the mapped output should be sent
    back to the server.
    Putting these requirements together, we arrive at the following implementation
    of the `server` method, which instantiates a new server:
    !*/

    /*!begin-code!*/
    def server[T, S](f: T => S): Server[T, S] = {
      val c = system.channels.open[Req[T, S]]
      c.events onMatch {
        case (x, reply) => reply ! f(x)
      }
      c.channel
    }
    /*!end-code!*/

    /*!md
    The `server` method starts by creating a connector for `Req[T, S]` type.
    It then adds a callback to the event stream of the newly created connector.
    The callback decomposes the request into the request value `x` of type `T` and
    the `reply` channel, then maps the input value using the specified mapping
    function `f`, and finally sends the mapped value of type `S` back along the `reply`
    channel. The `server` method returns the channel associated with this connector.

    We can use this method to start a server that maps input strings into
    uppercase strings, as follows:
    !*/

    /*!begin-code!*/
    val proto = Reactor[Unit] { self =>
      val s = server[String, String](_.toUpperCase)
    }
    system.spawn(proto)
    /*!end-code!*/

    /*!md
    Next, we should implement the client protocol.
    We will define a new method `?` on the `Channel` type,
    which sends the request to the server.
    This method cannot immediately return the server's response, because the response
    arrives asynchronously. Instead, `?` must return an event stream with the reply
    that is sent by the server.
    In conclusion, the `?` method must create a reply channel,
    send the `Req` object to the server, and then return the event stream associated
    with the reply channel.
    This is shown in the following:
    !*/

    /*!begin-code!*/
    implicit class ChannelOps[T, S: Arrayable](val s: Server[T, S]) {
      def ?(x: T): Events[S] = {
        val reply = system.channels.daemon.open[S]
        s ! (x, reply.channel)
        reply.events
      }
    }
    /*!end-code!*/

    /*!md
    We show the interaction between the server and the client by instantiating the
    two protocols within the same reactor.
    The server just returns an uppercase version of the input string,
    while the client sends the request with the content `"hello"`,
    and prints the response to the standard output.
    This is shown in the following snippet:
    !*/

    /*!begin-code!*/
    val serverClient = Reactor[Unit] { self =>
      val s = server[String, String](_.toUpperCase)

      (s ? "hello") onEvent { upper =>
        println(upper)
      }
    }
    system.spawn(serverClient)
    /*!end-code!*/

    /*!md
    Our implementation works, but it is not very useful to start the server-client
    protocol inside a single reactor. Normally, the server and the client are
    separated by the network, or are at least different reactors running inside the
    same reactor system.

    It turns out that, with our current toy implementation, it is not straightforward
    to instantiate the server-client protocol in two different reactors.
    The main reason for this is that once the server channel is instantiated
    within one reactor, we have no way of *seeing* it in another reactor.
    We will see how to easily overcome this problem
    when using the standard server-client implementation in the Reactors framework.
    !*/

    done.future.map(t => assert(t == "HELLO"))
  }

  /*!md
  ### Standard Server-Client Protocol

  We have just seen an example implementation of the server-client protocol,
  which relies solely on the basic primitives provided by the Reactors framework.
  However, the implementation that was presented is very simplistic,
  and it disregards several important concerns.
  For example, how do we stop the server protocol?
  Then, in our toy example,
  we instantiated the server-client protocol in a single reactor,
  but is it possible to instantiate server-client in two different reactors?

  In this section, we take a close look at how the server-client protocol is exposed
  in the Reactors framework, and explain how some of the above concerns are addressed.

  // TODO: Complete.
  !*/

}
