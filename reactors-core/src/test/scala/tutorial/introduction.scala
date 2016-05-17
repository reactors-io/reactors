package tutorial



/*!md
## Event streams

The first step in any program using reactors is to import the contents of the
`io.reactors` package. The following line will allow us to declare event streams,
channels and reactors:
!*/
/*!begin-code!*/
import io.reactors._
/*!end-code!*/
import org.scalatest._



class Introduction extends FunSuite with Matchers {
  /*!md
  So far, so good!
  Now, let's study the basic data-type that drives most computations in the Reactors.IO
  framework -- an *event stream*. Event streams represent special program values that
  can occasionally produce events. Event streams are represented by the `Event[T]` type.
  Here is an event stream `myEvents`, which produces string events:
  !*/

  def createEventStream() = new Events.Never[String]

  /*!begin-code!*/
  val myEvents: Events[String] = createEventStream()
  /*!end-code!*/

  /*!md
  To be useful, an event stream must allow the users to somehow manipulate the events it
  produces. For this purpose, every event stream has a method called `onEvent`, which
  takes a user callback function and invokes it every time an event arrives:
  !*/

  def myEventsUsage() {
    /*!begin-code!*/
    myEvents.onEvent(x => println(x))
    /*!end-code!*/
  }

  /*!md
  The `onEvent` method is similar to what most callback-based frameworks offer -- a way
  to provide an executable snippet of code that is invoked later, once an event becomes
  available. However, the receiver of the `onEvent` method, the event stream, is a
  *first-class* value. This subtle difference allows passing the event stream as an
  argument to other methods, and consequently write more general abstractions. For
  example, we can implement a reusable `trace` method as follows:
  !*/

  /*!begin-code!*/
  def trace[T](events: Events[T]) {
    events.onEvent(println)
  }
  /*!end-code!*/

  /*!md
  Before we continue, we note that event streams are entirely a single-threaded entity.
  The same event stream will never concurrently produce two events at the same time, so
  the `onEvent` method will never be invoked by two different threads at the same time
  on the same event stream. As we will see, this property simplifies the programming
  model and makes event-based programs easier to reason about.

  To understand this better, let's study a concrete event stream called *an emitter*,
  represented by the `Events.Emitter[T]` type. In the following, we instantiate an
  emitter:
  !*/

  test("emitter react") {
    /*!begin-code!*/
    val emitter = new Events.Emitter[Int]
    /*!end-code!*/

    /*!md
    An emitter is simultaneously an event stream and an *event source*. We can
    imperatively "tell" it to produce an event by calling its `react` method. When we do
    that, emitter invokes the callbacks previously registered with the `onEvent` method.
    !*/

    /*!begin-code!*/
    var luckyNumber = 0
    emitter.onEvent(luckyNumber = _)
    emitter.react(7)
    assert(luckyNumber == 7)
    /*!end-code!*/

    /*!md
    By running the above snippet, we convince ourselves that `react` really forces the
    emitter to produce an event.
    !*/
  }

  /*!md
  ## Lifecycle of an event stream  

  We now take a closer look at the events that an event stream can produce. An event
  stream of type `Events[T]` usually emits events of type `T`. However, `T` is not the
  only type of events that an event stream can produce. Some event streams are finite.
  When they emit all their events, they can `unreact` -- emit a special event that
  denotes that there will be no further events. And sometimes, event streams cause
  exceptional situations, and emit exceptions instead of normal events.

  The `onEvent` method that we saw earlier can only react to normal events. To listen to
  other event kinds, event streams have the more general `onReaction` method. The
  `onReaction` method takes an `Observer` object as an argument -- an `Observer` has
  three different methods used to react to different event types. In the following, we
  instantiate an emitter and listen to all its events:
  !*/

  test("emitter lifecycle") {
    /*!begin-code!*/
    var seen = List[Int]()
    var errors = List[String]()
    var done = 0
    val e = new Events.Emitter[Int]
    e.onReaction(new Observer[Int] {
      def react(x: Int, hint: Any) = seen ::= x
      def except(t: Throwable) = errors ::= t.getMessage
      def unreact() = done += 1
    })
    /*!end-code!*/

    /*!md
    The `Observer[T]` type has three methods:

    - `react`: Invoked when a normal event is emitted. The second, optional `hint`
      argument may contain an additional value, but is usually set to `null`.
    - `except`: Invoked when the event stream produces an exception. An event stream can
      produce multiple exceptions -- an exception does not terminated the stream.
    - `unreact`: Invoked when the event stream unreacts, i.e. stops producing events.
      After this method is invoked on the observer, no further events nor exceptions
      will be produced by the event stream.

    Let's assert that this contract is correct for `Events.Emitter`. We already learned
    that we can produce events with emitters by calling `react`. We can similarly call
    `except` to produce exceptions, or `unreact` to signal that there will be no more
    events. For example:
    !*/

    /*!begin-code!*/
    e.react(1)
    assert(seen == 1 :: Nil)
    e.react(2)
    assert(seen == 2 :: 1 :: Nil)
    assert(done == 0)
    e.except(new Exception("^_^"))
    assert(errors == "^_^" :: Nil)
    e.react(3)
    assert(seen == 3 :: 2 :: 1 :: Nil)
    assert(done == 0)
    e.unreact()
    assert(done == 1)
    e.react(4)
    e.except(new Exception("o_O"))
    assert(seen == 3 :: 2 :: 1 :: Nil)
    assert(errors == "^_^" :: Nil)
    assert(done == 1)
    /*!end-code!*/

    /*!md
    As you can see above, after `unreact`, subsequent calls to `react` or `except` have
    no effect -- `unreact` effectively terminates the emitter. Furthermore, we can see
    that emitters are simultaneoulsy event streams and observers. Not all event streams
    are as imperative as emitters, however. Most other event streams are created by
    composing different event streams.
    !*/
  }

  /*!md
  ## Functional composition of event streams

  In the previous sections, we saw how `onEvent` and `onReaction` methods work. In
  addition to these two, there are a few additional ways to add a callback to an event
  stream, as shown in the following example:
  !*/

  test("onX family") {
    /*!begin-code!*/
    val e = new Events.Emitter[String]
    e.onEventOrDone(x => println(x))(() => println("done, she's unreacted!"))
    e.onDone(println("event stream done!"))
    e onMatch {
      case "ok" => println("a-ok!")
    }
    e on {
      println("some event, but I don't care which!")
    }
    /*!end-code!*/
  }

  /*!md
  However, using only the `onX` family of event stream methods can easily result in a
  *callback hell* -- a program composed of a large number of unstructured `onX` calls,
  which is hard to understand and maintain. Having first-class event streams is a step
  in the right direction, but it is not sufficient.

  Event streams support *functional composition* -- a programming pattern that allows
  forming complex values by composing simpler ones. Consider the following example:
  !*/

}
