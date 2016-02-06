package org.reactors



import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import org.reactors.test._
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

  test("scanPast") {
    val buffer = mutable.Buffer[String]()
    val emitter = new Events.Emitter[String]
    val longest = emitter.scanPast("") { (prev, x) =>
      if (prev.length < x.length) x else prev
    }
    val sub = longest.onEvent(buffer += _)

    emitter.react("one")
    emitter.react("two")
    emitter.react("three")
    emitter.react("five")
    emitter.react("seven")
    emitter.react("eleven")

    assert(buffer == Seq("one", "one", "three", "three", "three", "eleven"))
  }

  test("scanPast with Int") {
    val buffer = mutable.Buffer[Int]()
    val emitter = new Events.Emitter[Int]
    val sum = emitter.scanPast(0)(_ + _)
    val sub = sum.onEvent(buffer += _)

    emitter.react(0)
    emitter.react(1)
    emitter.react(2)
    emitter.react(3)
    emitter.react(4)
    emitter.react(5)

    assert(buffer == Seq(0, 1, 3, 6, 10, 15))
  }

  test("toSignal") {
    val emitter = new Events.Emitter[Int]
    val signal = emitter.toSignal

    intercept[NoSuchElementException] {
      signal()
    }

    emitter.react(7)
    assert(signal() == 7)

    signal.unsubscribe()

    emitter.react(11)
    assert(signal() == 7)
  }

  test("toSignalWith") {
    val emitter = new Events.Emitter[Int]
    val signal = emitter.toSignalWith(1)

    assert(signal() == 1)

    emitter.react(7)
    assert(signal() == 7)

    signal.unsubscribe()

    emitter.react(11)
    assert(signal() == 7)
  }

  test("count") {
    val buffer = mutable.Buffer[Int]()
    val emitter = new Events.Emitter[String]
    val sub = emitter.count.onEvent(buffer += _)

    emitter.react("a")
    emitter.react("b")
    emitter.react("c")

    assert(buffer == Seq(1, 2, 3))
  }

  test("mutate") {
    var len = 0
    val log = new Events.Mutable(mutable.Buffer[String]())
    val emitter = new Events.Emitter[String]
    val s1 = emitter.mutate(log) { buffer => s =>
      buffer += s
    }
    val s2 = log.onEvent(x => len = x.length)

    emitter.react("one")
    assert(len == 1)

    emitter.react("two")
    assert(len == 2)

    assert(log.content == Seq("one", "two"))
  }

  test("mutate2") {
    var len = 0
    val log1 = new Events.Mutable(mutable.Buffer[String]())
    val log2 = new Events.Mutable(mutable.Buffer[Int]())
    val emitter = new Events.Emitter[String]
    emitter.mutate(log1, log2) { (b1, b2) => s =>
      b1 += s
      b2 += len
    }
    log1.onEvent(b => len = b.length)

    emitter.react("ok")
    assert(log1.content == Seq("ok"))
    assert(log2.content == Seq(0))
  }

  test("mutate3") {
    var len = 0
    var last = ""
    val log1 = new Events.Mutable(mutable.Buffer[String]())
    val log2 = new Events.Mutable(mutable.Buffer[String]())
    val log3 = new Events.Mutable(mutable.Buffer[Int]())
    val emitter = new Events.Emitter[String]
    emitter.mutate(log1, log2, log3) { (b1, b2, b3) => s =>
      b1 += s
      b2 += last
      b3 += len
    }
    log1.onEvent(b => last = b.last)
    log2.onEvent(b => len = b.length)

    emitter.react("ok")
    assert(log1.content == Seq("ok"))
    assert(log2.content == Seq(""))
    assert(log3.content == Seq(0))
  }

  test("after") {
    var seen = false
    val emitter = new Events.Emitter[Int]
    val start = new Events.Emitter[Unit]
    val after = emitter.after(start)
    after.on(seen = true)

    emitter.react(0)
    assert(!seen)

    start.react(())
    emitter.react(1)
    assert(seen)
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