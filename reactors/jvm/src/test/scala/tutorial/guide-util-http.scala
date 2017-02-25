/*!md
---
layout: tutorial
title: HTTP Service
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/util-http/index.html
pagenum: 1
pagetot: 40
section: guide-util
---
!*/
package tutorial



import org.scalatest._
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.io.Source



/*!md
## Http Service

In this section, we show how to use the `Http` service.
The `Http` service allows each reactor to act as an HTTP server.
To use this service, we need to add the `reactors-http` dependency:

```scala
libraryDependencies +=
  "io.reactors" %% "reactors-http" % "<version>"
```
!*/
class GuideHttpService extends AsyncFunSuite {

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("simple server") {
    /*!md
    The `Http` service can be invoked by different reactors.
    A server instance is associated with the first reactor
    that requests a specific port. Until that reactor terminates,
    other reactors cannot use that port (but they are allowed to request other ports).
    After a reactor acquires a specific port, it effectively becomes the server
    at that port. Inbound requests for that port are by default handled on this reactor.

    To reply to user requests, the reactor then needs to define a set of routes.
    Each route specifies how to respond to user requests coming on a different
    path of the URI. Depending on the route, the reactor can respons with an HTML page,
    with plain text, or with a resource that has a custom MIME type.

    Let's start by importing the `io.reactors` and the `io.reactors.http` packages:
    !*/

    /*!begin-code!*/
    import io.reactors._
    import io.reactors.http._
    /*!end-code!*/

    /*!md
    Next, we create a new reactor system, used to hold the server reactor.
    !*/

    /*!begin-code!*/
    val system = ReactorSystem.default("test-system")
    /*!end-code!*/

    /*!md
    We can now declare the server reactor.
    Our reactor will first fetch the `Http` service singleton with the expression
    `self.system.service[Http]`, and then invoke the `at` method combined with either
    `text`, `html` or `resource` to install different routes. We will install three
    different routes: `/hello`, `/about` and `/contact`, along with the corresponding
    handlers. Note that in the case of the `/hello` handler, we read the `name`
    parameter from the map of request parameters, and use it to greet the user.
    !*/

    /*!begin-code!*/
    val server = Reactor[Unit] { self =>
      val http = self.system.service[Http]
      http.at(9500).text("/hello") { req =>
        val name = req.parameters.getOrElse("name", Seq("World")).head
        s"Hello, $name."
      }
      http.at(9500).html("/about") { req =>
        """
        <html>
        <head>
          <title>About</title>
        </head>
        <body>
          <h2>About this website</h2>
          <p>
            This website was server using the <code>Http</code> service,
            and was created for you by a custom reactor.
          </p>
        </body>
        </html>
        """
      }
      http.at(9500).resource("/contact")("text/xml") { req =>
        val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <note>
          <website>http://reactors.io</website>
          <source>https://github.com/reactors-io/reactors</source>
        </note>
        """.trim
        new java.io.ByteArrayInputStream(xml.getBytes)
      }
    }
    /*!end-code!*/

    /*!md
    Having defined the server reactor prototype, we next need to spawn it.
    This is done as follows:
    !*/

    /*!begin-code!*/
    system.spawn(server)
    /*!end-code!*/

    Thread.sleep(500)
    val hello = Source.fromURL("http://localhost:9500/hello?name=Pluto").mkString
    assert(hello == "Hello, Pluto.")
    val about = Source.fromURL("http://localhost:9500/about").mkString
    assert(about.contains("<h2>About this website</h2>"))
    val contact = Source.fromURL("http://localhost:9500/contact").mkString
    assert(contact.contains("<website>http://reactors.io</website>"))

    /*!md
    If necessary, you can assign a custom scheduler to your server reactor.
    For example, if you want to ensure that your server gets a high priority,
    you can use the custom thread scheduler. See the Schedulers section
    for more details.

    You can now point your browser to
    [http://localhost:9500/hello](http://localhost:9500/hello),
    and see your web page in action.


    ## Parallel Servers

    It is also possible to create a server that forwards incoming messages
    to other workers. In this case, the parallelism of the server is improved,
    but the workers are not allowed to access the local state of the reactor.
    To create such a server, we need to pass the worker channel to the `at` method
    the first time it gets called.

    We start by creating a batch of workers -- we will import the `io.reactors.protocol`
    package to more easily create the workers and combine their input channels
    (see the Protocols section for more details):
    !*/

    /*!begin-code!*/
    val parallelServer = Reactor[Unit] { self =>
      import io.reactors.protocol._
      val workers = for (i <- 0 until 8) yield
        system.spawnLocal[Http.Connection] {
          self => self.main.events.onEvent(_.accept())
        }
      val workerChannel = self.system.channels.router[Http.Connection]
        .route(Router.roundRobin(workers)).channel
    /*!end-code!*/

    /*!md
    Now that we have our worker list, we can start the parallel HTTP server,
    and spawn the server reactor:
    !*/

    /*!begin-code!*/
      self.system.service[Http].parallel(9502, workerChannel)
        .text("/round") { req =>
          s"Round and round it goes -- ${Reactor.self.uid}!"
        }
    }

    system.spawn{parallelServer}
    /*!end-code!*/

    Thread.sleep(500)
    val round = Source.fromURL("http://localhost:9502/round").mkString
    assert(round.contains("Round and round it goes --"))

    /*!md
    Again, note that the handler for `/round` is not allowed to access any local
    reactor state in this example, since the handler will be invoked in parallel
    by different workers.
    !*/

    Thread.sleep(2500)
    system.shutdown()

    Promise.successful(assert(true)).future
  }
}
