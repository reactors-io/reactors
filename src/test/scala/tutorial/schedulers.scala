/*!md
---
layout: tutorial
title: Schedulers and Reactor Lifecycle
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/schedulers/index.html
pagenum: 3
pagetot: 40
section: guide
---
!*/
package tutorial



/*!md
## Schedulers

TODO

We start with the import of the standard Reactors.IO package:
!*/
/*!begin-code!*/
import io.reactors._
/*!end-code!*/
/*!include-code Java:reactors-java-schedulers-import.html!*/
import org.scalatest._


object SchedulersMockup {
  val fakeSystem = new FakeSystem
  def println(x: String) = fakeSystem.out.println(x)
}
import SchedulersMockup._


/*!md
We then define a reactor that logs incoming events,
reports every time it gets scheduled,
and ends after being scheduled three times:
!*/
/*!begin-code!*/
class Logger extends Reactor[String] {
  var count = 3
  sysEvents onMatch {
    case ReactorScheduled =>
      println("scheduled")
    case ReactorPreempted =>
      count -= 1
      if (count == 0) {
        main.seal()
        println("terminating")
      }
  }
  main.events.onEvent(println)
}
/*!end-code!*/
/*!include-code Java:reactors-java-schedulers-system.html!*/


class Schedulers extends FunSuite with Matchers {
  /*!md
  We then create a reactor system, as we saw in the previous sections,
  !*/

  /*!begin-code!*/
  val system = new ReactorSystem("test-system")
  /*!end-code!*/
  /*!include-code Java:reactors-java-schedulers-system.html!*/

  test("custom global execution context scheduler") {
    /*!md
    Every reactor system is bundled a default scheduler
    and some additional predefined schedulers.
    When a reactor is started, it uses the default scheduler,
    unless specified otherwise.
    In the following, we override the default scheduler with the one using Scala's
    global execution context, i.e. Scala's own default thread pool:
    !*/

    /*!begin-code!*/
    val proto = Proto[Logger].withScheduler(
      ReactorSystem.Bundle.schedulers.globalExecutionContext)
    val ch = system.spawn(proto)
    /*!end-code!*/
    /*!include-code Java:reactors-java-schedulers-global-ec.html!*/

    assert(fakeSystem.out.queue.take() == "scheduled")

    /*!md
    Running the snippet above should start the `Logger` reactor and print `scheduled`
    once, because starting a reactor schedules it even before any event arrives.
    If we now send an event to the main channel, we will see `scheduled` printed again,
    followed by the event itself.
    !*/

    /*!begin-code!*/
    ch ! "event 1"
    /*!end-code!*/
    /*!include-code Java:reactors-java-schedulers-global-ec-send.html!*/

    assert(fakeSystem.out.queue.take() == "scheduled")
    assert(fakeSystem.out.queue.take() == "event 1")

    /*!md
    Sending the event again decrements the reactor's counter and seals the main channel,
    therefore terminating the reactor:
    !*/

    Thread.sleep(1000)

    /*!begin-code!*/
    ch ! "event 2"
    /*!end-code!*/
    /*!include-code Java:reactors-java-schedulers-global-ec-send-again.html!*/

    assert(fakeSystem.out.queue.take() == "scheduled")
    assert(fakeSystem.out.queue.take() == "event 2")
    assert(fakeSystem.out.queue.take() == "terminating")
  }
}
