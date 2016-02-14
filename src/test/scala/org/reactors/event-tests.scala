package org.reactors



import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import org.reactors.common.Ref
import org.reactors.test._
import scala.collection._



class EventsSpec extends FunSuite {

  class TestEmitter[T] extends Events.Emitter[T] {
    var unsubscriptionCount = 0
    def hasSubscriptions = demux != null && {
      demux match {
        case w: Ref[_] => w.get != null
        case _ => true
      }
    }
    override def onReaction(obs: Observer[T]) = new Subscription.Composite(
      super.onReaction(obs),
      new Subscription {
        def unsubscribe() = unsubscriptionCount += 1
      }
    )
  }

  test("closed emitter immediately unreacts") {
    val emitter = new Events.Emitter[Int]
    emitter.unreact()

    var done = false
    emitter.onDone(done = true)
    assert(done)
  }

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

  test("toEmptySignal") {
    val emitter = new Events.Emitter[Int]
    val signal = emitter.toEmptySignal

    intercept[NoSuchElementException] {
      signal()
    }

    emitter.react(7)
    assert(signal() == 7)

    signal.unsubscribe()

    emitter.react(11)
    assert(signal() == 7)
  }

  test("toSignal") {
    val emitter = new Events.Emitter[Int]
    val signal = emitter.toSignal(1)

    assert(signal() == 1)

    emitter.react(7)
    assert(signal() == 7)

    signal.unsubscribe()

    emitter.react(11)
    assert(signal() == 7)
  }

  test("toSignal unreacts observers when done") {
    val emitter = new TestEmitter[Int]
    val signal = emitter.toSignal(7)
    emitter.unreact()

    var done = false
    signal.onDone(done = true)
    assert(done)
    assert(!emitter.hasSubscriptions)
  }

  test("toCold") {
    val emitter = new Events.Emitter[Int]
    val signal = emitter.toCold(1)

    assert(signal() == 1)

    emitter.react(7)
    assert(signal() == 1)

    var last = 0
    val sub0 = signal.onEvent(last = _)
    emitter.react(11)
    assert(signal() == 11)
    assert(last == 11)

    sub0.unsubscribe()
    emitter.react(17)
    assert(signal() == 11)
    assert(last == 11)

    val sub1 = signal.onEvent(last = _)
    emitter.react(19)
    assert(signal() == 19)
    assert(last == 19)
  }

  test("toCold unsubscribes with zero subscribers") {
    val emitter = new TestEmitter[Int]
    val signal = emitter.toCold(7)

    var last = 0
    val sub0 = signal.onEvent(last = _)

    emitter.react(11)
    assert(last == 11)

    sub0.unsubscribe()
    assert(emitter.unsubscriptionCount == 1)

    val sub1 = signal.onEvent(last = _)
    val sub2 = signal.onEvent(last = _)

    sub1.unsubscribe()
    assert(emitter.unsubscriptionCount == 1)
    sub2.unsubscribe()
    assert(emitter.unsubscriptionCount == 2)
  }

  test("toCold unreacts observers when done") {
    val emitter = new TestEmitter[Int]
    val signal = emitter.toCold(7)
    assert(!emitter.hasSubscriptions)
    signal.on({})
    assert(emitter.hasSubscriptions)
    emitter.unreact()
    assert(!emitter.hasSubscriptions)

    var done = false
    signal.onDone(done = true)
    assert(done)
    assert(!emitter.hasSubscriptions)
  }

  test("toCold used with zip removes subscriptions") {
    val e0 = new TestEmitter[Int]
    val e1 = new TestEmitter[Int]
    var done = false
    (e0.toCold(3) zip e1.toCold(7))(_ + _).onDone(done = true)

    e0.react(1)
    e1.react(2)
    e0.unreact()
    assert(done)
    assert(!e0.hasSubscriptions)
    assert(!e1.hasSubscriptions)
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

    emitter.react(7)
    assert(!seen)

    start.react(())
    emitter.react(11)
    assert(seen)
  }

  test("after with Int") {
    var seen = false
    val emitter = new Events.Emitter[Int]
    val start = new Events.Emitter[Int]
    val after = emitter.after(start)
    after.on(seen = true)

    emitter.react(7)
    assert(!seen)

    start.react(11)
    emitter.react(17)
    assert(seen)
  }

  test("after unsubscribes") {
    val emitter = new Events.Emitter[Int]
    val start = new TestEmitter[Int]
    emitter.after(start).on({})

    assert(start.unsubscriptionCount == 0)
    start.react(1)
    assert(start.unsubscriptionCount == 1)
  }

  test("until") {
    var sum = 0
    val emitter = new Events.Emitter[Int]
    val end = new Events.Emitter[Int]
    val until = emitter.until(end)
    until.onEvent(sum += _)

    emitter.react(7)
    assert(sum == 7)

    emitter.react(19)
    assert(sum == 26)

    end.react(11)
    emitter.react(17)
    assert(sum == 26)
  }

  test("until unsubscribes") {
    val emitter = new TestEmitter[Int]
    val end = new TestEmitter[Int]
    emitter.until(end).on({})

    assert(emitter.unsubscriptionCount == 0)
    assert(end.unsubscriptionCount == 0)
    end.react(1)
    assert(emitter.unsubscriptionCount == 1)
    assert(end.unsubscriptionCount == 1)
  }

  test("once") {
    var count = 0
    var done = 0
    val emitter = new Events.Emitter[Int]
    val once = emitter.once
    once.on(count += 1)
    once.onDone(done += 1)

    emitter.react(7)
    assert(count == 1)
    assert(done == 1)

    emitter.react(11)
    assert(count == 1)
    assert(done == 1)
  }

  test("once with early unreact") {
    var seen = false
    val emitter = new Events.Emitter[String]
    val once = emitter.once
    once.on(seen = true)

    emitter.unreact()
    assert(!seen)

    emitter.react("kaboom")
    assert(!seen)
  }

  test("once unsubscribes") {
    val emitter = new TestEmitter[Int]
    emitter.once.on({})

    assert(emitter.unsubscriptionCount == 0)
    emitter.react(1)
    assert(emitter.unsubscriptionCount == 1)
  }

  test("filter") {
    val buffer = mutable.Buffer[Int]()
    val emitter = new Events.Emitter[Int]
    emitter.filter(_ % 2 == 0).onEvent(buffer += _)

    emitter.react(1)
    assert(buffer == Seq())

    emitter.react(4)
    assert(buffer == Seq(4))

    emitter.react(9)
    assert(buffer == Seq(4))

    emitter.react(10)
    assert(buffer == Seq(4, 10))

    emitter.unreact()
    emitter.react(16)
    assert(buffer == Seq(4, 10))
  }

  test("collect") {
    val buffer = mutable.Buffer[String]()
    val emitter = new Events.Emitter[String]
    val collect = emitter.collect {
      case "ok" => "ok!"
    }
    collect.onEvent(buffer += _)

    emitter.react("not ok")
    assert(buffer == Seq())

    emitter.react("ok")
    assert(buffer == Seq("ok!"))
  }

  test("map") {
    val buffer = mutable.Buffer[String]()
    val emitter = new Events.Emitter[Int]
    emitter.map(_.toString).onEvent(buffer += _)

    emitter.react(7)
    assert(buffer == Seq("7"))

    emitter.react(11)
    assert(buffer == Seq("7", "11"))
  }

  test("takeWhile") {
    val buffer = mutable.Buffer[String]()
    val emitter = new Events.Emitter[String]
    emitter.takeWhile(_.length < 5).onEvent(buffer += _)

    emitter.react("one")
    emitter.react("four")
    emitter.react("seven")
    emitter.react("ten")

    assert(buffer == Seq("one", "four"))
  }

  test("takeWhile unsubscribes early") {
    val emitter = new TestEmitter[Int]
    emitter.takeWhile(_ < 3).on({})

    emitter.react(1)
    assert(emitter.unsubscriptionCount == 0)
    emitter.react(2)
    assert(emitter.unsubscriptionCount == 0)
    emitter.react(3)
    assert(emitter.unsubscriptionCount == 1)
  }

  test("dropWhile") {
    val buffer = mutable.Buffer[String]()
    val emitter = new Events.Emitter[String]
    emitter.dropWhile(_.length < 5).onEvent(buffer += _)

    emitter.react("one")
    emitter.react("two")
    emitter.react("three")
    emitter.react("nil")

    assert(buffer == Seq("three", "nil"))
  }

  test("mux") {
    var sum = 0
    val emitter = new Events.Emitter[Events[Int]]
    emitter.mux.onEvent(sum += _)

    val e1 = new Events.Emitter[Int]
    val e2 = new Events.Emitter[Int]
    e1.react(3)
    e2.react(5)
    assert(sum == 0)

    emitter.react(e1)
    e1.react(7)
    e2.react(11)
    assert(sum == 7)

    emitter.react(e2)
    e1.react(17)
    e2.react(19)
    assert(sum == 26)

    e2.unreact()
    emitter.react(e1)
    e1.react(23)
    assert(sum == 49)

    emitter.unreact()
    e1.react(29)
    assert(sum == 78)
  }

  test("unreacted") {
    var count = 0
    val emitter = new Events.Emitter[Int]
    emitter.unreacted.on(count += 1)

    emitter.react(5)
    assert(count == 0)
    emitter.unreact()
    assert(count == 1)
  }

  test("unreacted unsubscribes early") {
    val emitter = new TestEmitter[Int]
    emitter.unreacted.on({})
    emitter.unreact()
    assert(emitter.unsubscriptionCount == 1)
  }

  test("union") {
    var done = false
    val buffer = mutable.Buffer[String]()
    val e0 = new Events.Emitter[String]
    val e1 = new Events.Emitter[String]
    val union = e0 union e1
    union.onEvent(buffer += _)
    union.onDone(done = true)

    e0.react("bam")
    assert(buffer == Seq("bam"))
    e1.react("boom")
    assert(buffer == Seq("bam", "boom"))
    e0.react("dam")
    assert(buffer == Seq("bam", "boom", "dam"))
    e0.unreact()
    e1.react("van dam")
    assert(buffer == Seq("bam", "boom", "dam", "van dam"))
    assert(!done)
    e1.unreact()
    assert(done)
  }

  test("concat") {
    var done = false
    val buffer = mutable.Buffer[Int]()
    val e0 = new Events.Emitter[Int]
    val e1 = new Events.Emitter[Int]
    val concat = e0 concat e1
    concat.onEvent(buffer += _)
    concat.onDone(done = true)

    e0.react(3)
    assert(buffer == Seq(3))
    e1.react(7)
    assert(buffer == Seq(3))
    e0.react(5)
    assert(buffer == Seq(3, 5))
    e0.unreact()
    assert(buffer == Seq(3, 5, 7))
    e1.react(11)
    assert(buffer == Seq(3, 5, 7, 11))
    assert(!done)
    e1.unreact()
    assert(done)
  }

  test("sync") {
    var done = false
    val buffer = mutable.Buffer[Int]()
    val e0 = new Events.Emitter[Int]
    val e1 = new Events.Emitter[Int]
    val sync = (e0 sync e1)(_ + _)
    sync.onEvent(buffer += _)
    sync.onDone(done = true)

    e0.react(3)
    assert(buffer == Seq())
    e1.react(5)
    assert(buffer == Seq(8))
    e1.react(7)
    e1.react(11)
    assert(buffer == Seq(8))
    e0.react(19)
    assert(buffer == Seq(8, 26))
    assert(!done)
    e1.unreact()
    assert(buffer == Seq(8, 26))
    assert(done)
  }

  test("postfix union") {
    var done = false
    val buffer = mutable.Buffer[Int]()
    val emitter = new Events.Emitter[Events.Emitter[Int]]
    val e0 = new Events.Emitter[Int]
    val es = for (i <- 0 until 5) yield new Events.Emitter[Int]
    val union = emitter.union
    union.onEvent(buffer += _)
    union.onDone(done = true)

    emitter.react(e0)
    assert(!done)
    for (e <- e0 +: es) e.react(7)
    assert(buffer == Seq(7))
    for (e <- es) emitter.react(e)
    for (e <- es) e.react(11)
    assert(buffer == Seq(7, 11, 11, 11, 11 ,11))
    assert(!done)
    e0.unreact()
    assert(!done)
    for (e <- es) e.react(17)
    assert(buffer == Seq(7, 11, 11, 11, 11 ,11, 17, 17, 17, 17, 17))
    emitter.unreact()
    assert(!done)
    for (e <- es) e.unreact()
    assert(done)
  }

  test("postfix concat") {
    var done = false
    val buffer = mutable.Buffer[Int]()
    val emitter = new Events.Emitter[Events.Emitter[Int]]
    val e0 = new Events.Emitter[Int]
    val es = for (i <- 0 until 5) yield new Events.Emitter[Int]
    val e6 = new Events.Emitter[Int]
    val concat = emitter.concat
    concat.onEvent(buffer += _)
    concat.onDone(done = true)

    emitter.react(e0)
    assert(!done)
    for (e <- e0 +: es) e.react(7)
    assert(buffer == Seq(7))
    for (e <- es) emitter.react(e)
    emitter.react(e6)
    for (e <- es) e.react(11)
    assert(buffer == Seq(7))
    e0.react(11)
    assert(buffer == Seq(7, 11))
    e6.react(17)
    assert(buffer == Seq(7, 11))
    assert(!done)
    e0.unreact()
    assert(!done)
    assert(buffer == Seq(7, 11, 11))
    for (e <- es) e.react(19)
    es.head.unreact()
    assert(buffer == Seq(7, 11, 11, 19, 11, 19))
    for (e <- es) e.unreact()
    assert(buffer == Seq(7, 11, 11, 19, 11, 19, 11, 19, 11, 19, 11, 19, 17))
    emitter.unreact()
    assert(!done)
    e6.react(23)
    assert(buffer == Seq(7, 11, 11, 19, 11, 19, 11, 19, 11, 19, 11, 19, 17, 23))
    e6.unreact()
    assert(done)
  }

  test("ivar") {
    var result = 0
    var done = 0
    val emitter = new TestEmitter[Int]
    val ivar = emitter.toIvar
    ivar.onEvent(result += _)
    ivar.onDone(done += 11)

    assert(emitter.hasSubscriptions)
    emitter.react(7)
    assert(result == 7)
    assert(done == 11)
    assert(ivar() == 7)
    assert(ivar.isAssigned)
    assert(ivar.isCompleted)
    assert(!ivar.isFailed)
    assert(!ivar.isUnassigned)
    assert(!emitter.hasSubscriptions)

    emitter.react(17)
    assert(result == 7)
    assert(done == 11)
    assert(ivar() == 7)
    assert(ivar.isAssigned)
    assert(ivar.isCompleted)
    assert(!ivar.isFailed)
    assert(!ivar.isUnassigned)

    emitter.unreact()
    assert(!emitter.hasSubscriptions)
    var last = 0
    var terminated = false
    ivar.onEventOrDone(last = _)(terminated = true)
    assert(last == 7)
    assert(terminated)
    assert(!emitter.hasSubscriptions)
  }

  test("ivar with exception") {
    var result = 0
    var done = 0
    var e: Throwable = null
    val emitter = new Events.Emitter[Int]
    val ivar = emitter.toIvar
    ivar.onReaction(new Observer[Int] {
      def react(x: Int) = result += x
      def except(t: Throwable) = e = t
      def unreact() = done = 11
    })

    emitter.except(new IllegalArgumentException)
    assert(done == 11)
    assert(ivar.failure.isInstanceOf[IllegalArgumentException])
    assert(!ivar.isAssigned)
    assert(ivar.isCompleted)
    assert(ivar.isFailed)
    assert(!ivar.isUnassigned)

    emitter.react(17)
    assert(done == 11)
    assert(ivar.failure.isInstanceOf[IllegalArgumentException])
    assert(!ivar.isAssigned)
    assert(ivar.isCompleted)
    assert(ivar.isFailed)
    assert(!ivar.isUnassigned)
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