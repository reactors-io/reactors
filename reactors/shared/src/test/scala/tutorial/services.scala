/*!md
---
layout: tutorial
title: Reactor System Services
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/services/index.html
pagenum: 4
pagetot: 40
section: guide
---
!*/
package tutorial



import io.reactors._
import org.scalatest._
import scala.concurrent._



class Log(val system: ReactorSystem) extends Protocol.Service {
  def apply(x: Any) = Log.example.success(x.toString)

  def shutdown() {}
}


object Log {
  var example: Promise[String] = null
}


/*!md
## Services

In the earlier sections,
we learned that reactors delimit concurrent executions,
and that event streams allow routing events within each reactor.
This is already a powerful set of abstractions,
and we can use reactors and event streams to write all kinds of distributed programs.
However, such a model is restricted to reactor computations only --
we cannot, for example, start blocking I/O operations, read from a temperature sensor,
wait until a GPU computation completes, or do logging.
In some cases,
we need to interact with the native capabilities of the OS,
or tap into a rich ecosystem of existing libraries.
For this purpose,
every reactor system has a set of **services** --
protocols that relate event streams to the outside world.

In this section,
we will take a closer look at various services that are available by default,
and also show how to implement custom services and plug them into reactor systems.
!*/
class ReactorSystemServices extends AsyncFunSuite {

  implicit override def executionContext = ExecutionContext.Implicits.global

  /*!md
  ### The logging service

  We will start with the simplest possible service called `Log`.
  This service is used to print logging messages to the standard output.
  In the following, we create an anonymous reactor that uses the `Log` service.
  We start by importing the `Log` service.
  !*/

  {
    /*!begin-code!*/
    import io.reactors.services.Log
    /*!end-code!*/
    /*!include-code Java:reactors-java-services-log-import.html!*/
  }

  /*!md
  Next, we create a reactor system, and start a reactor instance.
  The reactor invokes the `service` method on the reactor system,
  which returns the service with the specified type.
  The reactor then calls the `apply` method on the `log` to print a message,
  and seals itself.
  !*/
  test("logging service") {
    Log.example = Promise[String]()

    val system = ReactorSystem.default("tutorial-log-system")
    try {
      /*!begin-code!*/
      system.spawn(Reactor[String] { self =>
        val log = system.service[Log]
        log("Test reactor started!")
        self.main.seal()
      })
      /*!end-code!*/
      /*!include-code Java:reactors-java-services-log-example.html!*/
    } finally {
      system.shutdown()
    }

    /*!md
    Running the above snippet prints the timestamped message to the standard output.

    This example is very simple, but we use it to describe some
    important properties of services.

    - Reactor system's method `service[S]` returns a service of type `S`.
    - The service obtained this way is a lazily initialized singleton instance -- there
      exists at most one instance of the service per reactor system, and it is created
      only after being requested by some reactor.
    - Some standard services are eagerly initialized when the reactor system gets
      created. Such services are usually available as a standalone method on the
      `ReactorSystem` class. For example, `system.log` is an alternative way to obtain
      the `Log` service.
    !*/

    Log.example.future.map(s => assert(s.contains("Test reactor started!")))
  }

  /*!md
  ### The clock service

  Having seen a trivial service example,
  let's take a look at a more involved service that connects reactors with
  the outside world of events, namely, the `Clock` service.
  The `Clock` service is capable of producing time-driven events,
  for example, timeouts, countdowns or periodic counting.
  This service is standard, so it is also available by calling either `system.clock`
  or `system.service[Clock]` - both return the same instance.
  !*/

  test("clock service") {
    val system = ReactorSystem.default("tutorial-clock-system")

    /*!md
    In the following, we create an anonymous reactor that uses the `Clock` service
    to create a timeout event after 1 second. The `timeout` method of the clock service
    returns an event stream of the `Unit` type
    (more specifically, it returns an `IVar` event stream, i.e. a single-assignment
    variable, which always produces at most one event).
    We install a callback to the `timeout` event stream, which seals the main channel
    channel of this reactor.
    !*/

    /*!begin-code!*/
    import scala.concurrent.duration._

    val done = Promise[Boolean]()
    system.spawn(Reactor[String] { self =>
      system.clock.timeout(1.second) on {
        done.success(true)
        self.main.seal()
      }
    })
    /*!end-code!*/
    /*!include-code Java:reactors-java-services-timeout.html!*/

    /*!md
    The `Clock` service uses a separate timer thread under-the-hood, which sends events
    to the reactor when the timer thread decides it is time to do so. The events are
    sent on a special channel created by the `timeout` method, so they are seen only
    on the corresponding event stream combinator.
    Graphically, this looks as follows:

    ```
                       #---------------------------#
                       |                           |
                       |                           |
       main.channel ---o--> main.events            |
                       |                           |
                       o--> sysEvents              |
                       |                           |
      Channel[Unit] ---o--> system.clock.timeout   |
            ^          |                           |
            |          |                           |
     <Timer thread>    |                           |
                       |                           |
                       #---------------------------#
    ```
    When the main channel gets sealed, the reactor terminates - the `timeout` event
    stream creates a daemon channel under-the-hood, which does not prevent our anonymous
    reactor from terminating after non-daemon channels are gone.

    The `Clock` service depicts a general pattern - when a native entity or an external
    event needs to communicate with a reactor, it creates a new channel, and then
    asynchronously sends events to it.
    !*/

    done.future.onComplete(_ => system.shutdown())
    done.future.map(t => assert(t))
  }

  /*!md
  ### The channels service

  Some services provide event streams that work with reactor system internals.
  The `Channels` service is a good example - it provides an event-drive view over all
  channels that exist in the current reactor system. This allows polling the channels
  that are currently available, or awaiting for a channel with a specific name to become
  available. Awaiting for a channel is particularly useful, as it allows to battle
  asynchrony between reactors, which is inherent to distributed systems.

  As a side-note, we actually saw the `Channels` service earlier, when we used it to
  open a second channel in a reactor. The expression `system.channels.open` actually
  calls the `open` method on the standard channel service.
  !*/

  test("channels service") {
    val system = ReactorSystem.default("tutorial-channels-system")

    /*!md
    In the following, we will construct two reactors. The first reactor will create
    a specially named channel after some delay, and the second reactor will await that
    channel. When the channel appears, the second reactor will send an event to that
    channel.
    !*/

    import scala.concurrent.duration._

    /*!begin-code!*/
    val done = Promise[Boolean]()
    val first = Reactor[String] { self =>
      system.clock.timeout(1.second) on {
        val c = system.channels.daemon.named("lucky").open[Int]
        c.events on {
          done.success(true)
          self.main.seal()
        }
      }
    }
    system.spawn(first.withName("first"))

    system.spawn(Reactor[String] { self =>
      system.channels.await[Int]("first", "lucky") onEvent { ch =>
        ch ! 7
        self.main.seal()
      }
    })
    /*!end-code!*/
    /*!include-code Java:reactors-java-services-channels.html!*/

    /*!md
    Above, we use the `Clock` service seen earlier to introduce a delay in the
    `first` reactor. In the second reactor, we use the `Channels` service to wait
    for the channel named `"lucky"` of the reactor named `"first"`.
    Both reactors start approximately at the same time.

    After one second, the `first` reactor uses the `Channels` service to open a new
    daemon channel named `"lucky"`. The first reactor then installs a callback:
    when the first event arrives on the lucky channel,
    a promise object (used for our testing purposes) must be completed,
    and the main channel must be sealed, so that the reactor terminates.

    The second reactor gets an event from the `Channels` service - a
    channel with the requested name exists, and the second reactor can use it
    to communicate. The second reactor sends `7` to the lucky channel, and terminates.

    To reiterate, awaiting channels is crucial when establishing temporal order in
    an asynchronous system, and the `Channels` service is a useful tool for
    this purpose.
    !*/

    done.future.onComplete(_ => system.shutdown())
    done.future.map(t => assert(t))
  }
}
