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

    /*!begin-code!*/
    val system = ReactorSystem.default("tutorial-system")

    system.spawn(Reactor[String] { self =>
      val log = system.service[Log]
      log("Reactor started!")
      self.main.seal()
    })
    /*!end-code!*/

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

    Log.example.future.map(s => assert(s.contains("Reactor started!")))
  }

  /*!md
  ## The clock service

  Having seen a trivial service example,
  let's take a look at a more involved service that connects reactors with
  the outside world of events, namely, the `Clock` service.
  The `Clock` service is capable of producing time-driven events,
  for example, timeouts, countdowns or periodic counting.
  !*/
}
