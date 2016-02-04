package org.reactors



import org.scalatest._



class EventsSpec extends FunSuite {

  test("onReaction") {
    var event: String = null
    var exception: Throwable = null
    var done = false
    val emitter = new Events.Emitter[String]
    val sub = emitter.onReaction(new Observer[String] {
      def react(x: String) = event = x
      def except(t: Throwable) = exception = t
      def unreact() = done = true
    })

    emitter.react("ok")
    assert(event == "ok")
    assert(exception == null)
    assert(!done)

    val e = new RuntimeException("not ok")
    emitter.except(e)
    assert(event == "ok")
    assert(exception == e)
    assert(!done)

    emitter.unreact()
    assert(event == "ok")
    assert(exception == e)
    assert(done)

    emitter.react(null)
    emitter.except(null)
    assert(event == "ok")
    assert(exception == e)
    assert(done)
  }

  test("onReaction with early unsubscribe") {
    var event: String = null
    var exception: Throwable = null
    var done = false
    val emitter = new Events.Emitter[String]
    val sub = emitter.onReaction(new Observer[String] {
      def react(x: String) = event = x
      def except(t: Throwable) = exception = t
      def unreact() = done = true
    })

    emitter.react("ok")
    assert(event == "ok")
    assert(exception == null)
    assert(!done)

    sub.unsubscribe()

    emitter.react("hmph")
    assert(event == "ok")
    assert(exception == null)
    assert(!done)
  }

  test("onReactUnreact") {
    var event: String = null
    var done = false
    val emitter = new Events.Emitter[String]
    val sub = emitter.onReactUnreact {
      event = _
    } {
      done = true
    }

    emitter.react("ok")
    assert(event == "ok")
    assert(!done)

    emitter.unreact()
    assert(event == "ok")
    assert(done)
  }

}
