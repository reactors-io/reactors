/*!md
---
layout: tutorial
title: Router Protocol
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/protocol-router/index.html
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
## Router Protocol

In this section,
we take a look at a simple router protocol.
Here, events coming to a specific channel are routed between a set of target channels,
according to some user-specified policy.
In practice, there are a number of applications of this protocol,
ranging from data replication and sharding, to load-balancing and multicasting.
In our first example,
we will instantiate a master reactor that will route the incoming requests
between two workers.
For simplicity, requests will be just strings,
and the workers will just print those strings to the standard output.
!*/
class RouterProtocol extends AsyncFunSuite {

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("router master-workers") {
    val done1 = Promise[String]()
    val done2 = Promise[String]()
    def println(s: String) = {
      if (!done1.trySuccess(s)) done2.trySuccess(s)
    }

    /*!md
    There are several ways to instantiate the router protocol.
    First, the protocol can be started within an existing reactor,
    in which case it is just one of the protocols running inside that reactor.
    Second, the protocol can be started as a standalone reactor,
    in which case that reactor is dedicated to the router protocol.

    We will start by creating an instance of the router protocol in an existing reactor.
    We first import the contents of the `io.reactors`
    and the `io.reactors.protocol` packages,
    and then instantiate a default reactor system.
    !*/

    /*!begin-code!*/
    import io.reactors._
    import io.reactors.protocol._

    val system = ReactorSystem.default("test-system")
    /*!end-code!*/

    /*!md
    We can now use the reactor system to start two workers, `worker1` and `worker2`.
    We use a shorthand method `spawnLocal`, to concisely start the reactors
    without first creating the `Proto` object:
    */

    /*!begin-code!*/
    val worker1 = system.spawnLocal[String] { self =>
      self.main.events.onEvent(x => println(s"1: ${x}"))
    }
    val worker2 = system.spawnLocal[String] { self =>
      self.main.events.onEvent(x => println(s"2: ${x}"))
    }
    /*!end-code!*/

    /*!md
    Next, we declare a reactor whose main channel takes `Unit` events,
    since we will not be using the main channel for anything special.
    Inside that reactor, we first call `router[String]` on the `channels` service
    to open a connector for the router.
    Just calling the `router` method does not start the protocol - we need to call
    `route` on the connector to actually start routing.

    The `route` method expects a `Router.Policy` object as an argument.
    The policy object contains the routing logic for the router protocol.
    We will use the simple round-robin policy in this example.
    The `Router.roundRobin` factory method expects a list of channels
    for the round-robin policy, so we will pass a list with `worker1` and `worker2`:
    */

    /*!begin-code!*/
    system.spawnLocal[Unit] { self =>
      val router = system.channels.daemon.router[String]
        .route(Router.roundRobin(Seq(worker1, worker2)))
      router.channel ! "one"
      router.channel ! "two"
    }
    /*!end-code!*/

    /*!md
    Having instantiated the `router` protocol, we use the input `channel`
    associated with the router to send two events to the workers.

    After starting the router protocol and sending the events `"one"` and `"two"`
    to the router channel, the two strings are delivered to the two different
    workers. The `roundRobin` policy does not specify which of the target channels
    is chosen first, so the output could be the following two lines, in some order:

    ```
    1: one
    2: two
    ```

    Or, the following two lines, in some order:

    ```
    1: two
    2: one
    ```

    The round-robin routing policy does not have any knowledge about the two target
    channels, so it just picks one after another in succession, and then the first one
    again when it reaches the end of the target list. Effectively, this policy
    constitutes a very simple form of load-balancing.
    */

    val done = for {
      x <- done1.future
      y <- done2.future
    } yield (x, y)
    done.map(t => assert(
      t == ("1: one", "2: two") || t == ("2: two", "1: one") ||
      t == ("1: two", "2: one") || t == ("2: one", "1: two")
    ))
  }

  /*!md
  There are other predefined policies that can be used with the router protocol.
  For example, the `Router.random` policy uses a random number generator to route events
  to different channels, which is more robust in scenarios when a high-load event
  gets sent periodically. Another policy is `Router.hash`, which computes the hash code
  of the event, and uses it to find the target channel. If either of these are not
  satisfactory, `deficitRoundRobin` strategy tracks the expected cost of each event,
  and biases its routing decisions to balance the total cost sent to each target.

  The above-mentioned policies are mainly used in load-balancing.
  In addition, users can define their own custom policies for their use-cases.
  For example, if the router has some information about the current target availability,
  it can take that into account when making routing decisions.

  // TODO: Complete.
  */
}
