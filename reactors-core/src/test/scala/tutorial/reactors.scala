package tutorial



import io.reactors._
import org.scalatest._



class Reactors extends FunSuite with Matchers {
  /*!md
  ## Reactors

  As we learned previously, event streams always propagate events on a single thread.
  This is useful from the standpoint of program comprehension, but we still need a way
  to express concurrency in our programs. In this section, we will see how this is done
  with reactors.

  A reactor is the basic unit of concurrency. For readers familiar with the actor model,
  a reactor is close to the concept of an actor. While actors receive messages, reactors
  receive events. However, while an actor in particular state has only a single point in
  its definition where it can receive a message, a reactor can receive an event from
  many different sources at any time. Despite this flexibility, one reactor will always
  process **at most one** event at any time. We say that events received by a reactor
  *serialize*, similar to how messages received by an actor are serialized.

  To be able to create new reactors, we need a `ReactorSystem` object, which tracks
  reactors in a single machine.
  !*/

  /*!begin-code!*/
  val system = new ReactorSystem("test-system")
  /*!end-code!*/

  test("simple reactor") {
    /*!md
    Before we can start a reactor instance, we need to define its template. One way to
    do this is to extend the `Reactor` class. The following reactor prints all the
    events it receives to the standard output:
    !*/

    /*!begin-code!*/
    class HelloReactor extends Reactor[String] {
      main.events onEvent {
        x => println(x)
      }
    }
    /*!end-code!*/
  }
}
