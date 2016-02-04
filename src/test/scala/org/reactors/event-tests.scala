package org.reactors



import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import org.testx._
import scala.collection._



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

  test("onEventOrDone") {
    var event: String = null
    var done = false
    val emitter = new Events.Emitter[String]
    val sub = emitter.onEventOrDone {
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

  test("onEvent") {
    var event: String = null
    val emitter = new Events.Emitter[String]
    val sub = emitter.onEvent(event = _)
  
    emitter.react("ok")
    assert(event == "ok")
    
    sub.unsubscribe()
    
    emitter.react("lost")
    assert(event == "ok")
  }

  test("onMatch") {
    var event: String = null
    val emitter = new Events.Emitter[String]
    val sub = emitter onMatch {
      case x if x.length < 5 => event = x
    }

    emitter.react("ok")
    assert(event == "ok")

    emitter.react("long'n'lost")
    assert(event == "ok")

    sub.unsubscribe()

    emitter.react("boom")
    assert(event == "ok")
  }

  test("on") {
    var count = 0
    val emitter = new Events.Emitter[String]
    val sub = emitter.on(count += 1)

    emitter.react("bam")
    assert(count == 1)

    emitter.react("babaluj")
    assert(count == 2)

    sub.unsubscribe()
    
    emitter.react("foo")
    assert(count == 2)
  }

  test("onDone") {
    var done = false
    val emitter = new Events.Emitter[String]
    val sub = emitter.onDone(done = true)

    emitter.react("bam")
    assert(!done)

    emitter.unreact()
    assert(done)
  }

  test("onDone unsubscribe") {
    var done = false
    val emitter = new Events.Emitter[String]
    val sub = emitter.onDone(done = true)

    emitter.react("ok")
    assert(!done)

    sub.unsubscribe()

    emitter.unreact()
    assert(!done)
  }

  test("onExcept") {
    var found = false
    val emitter = new Events.Emitter[String]
    val sub = emitter onExcept {
      case e: IllegalArgumentException => found = true
      case _ => // ignore
    }

    emitter.except(new RuntimeException)
    assert(!found)

    emitter.except(new IllegalArgumentException)
    assert(found)
  }

  test("recover") {
    val buffer = mutable.Buffer[String]()
    val emitter = new Events.Emitter[String]
    val sub = emitter recover {
      case e: IllegalArgumentException => "kaboom"
    } onEvent(buffer += _)

    emitter.react("ok")
    assert(buffer == Seq("ok"))

    emitter.except(new IllegalArgumentException)
    assert(buffer == Seq("ok", "kaboom"))

    intercept[RuntimeException] {
      emitter.except(new RuntimeException)
    }
    
    sub.unsubscribe()
    
    emitter.except(new RuntimeException)
    assert(buffer == Seq("ok", "kaboom"))
  }

  test("ignoreExceptions") {
    var seen = false
    val emitter = new Events.Emitter[String]
    val sub = emitter.ignoreExceptions.on(seen = true)

    emitter.except(new RuntimeException)
    assert(!seen)
  }

}


class EventsCheck extends Properties("Events") with ExtendedProperties {

  val sizes = detChoose(0, 1000)

  property("should register observers") = forAllNoShrink(sizes) { size =>
    stackTraced {
      val buffer = mutable.Buffer[Int]()
      val emitter = new Events.Emitter[String]
      for (i <- 0 until size) emitter.onEvent(x => buffer += i)
  
      emitter.react("ok")
  
      buffer.toSet == (0 until size).toSet
    }
  }

  property("should deregister observers") = forAllNoShrink(sizes, sizes) { (add, rem) =>
    stackTraced {
      val buffer = mutable.Buffer[Int]()
      val emitter = new Events.Emitter[String]
      val subs = for (i <- 0 until add) yield emitter.onEvent(x => buffer += i)
      for (i <- 0 until math.min(add, rem)) subs(i).unsubscribe()
  
      emitter.react("ok")
  
      buffer.toSet == (math.min(add, rem) until add).toSet
    }
  }

}