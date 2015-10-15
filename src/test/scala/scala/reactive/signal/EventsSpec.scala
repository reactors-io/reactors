package scala.reactive
package signal



import org.scalatest._
import org.scalatest.exceptions.TestFailedException
import scala.collection._



class EventsSpec extends FlatSpec with Matchers {

  class ReactiveTest {
    val x = RCell(0)
    val y = RCell(0)
    val z = RCell(0)
    val w = RCell(0)
  }

  "A reactive" should "be filtered" in {
    val rt = new ReactiveTest
    val s = rt.x.filter {
      _ % 2 == 0
    }
    val a = s foreach { case x =>
      assert(x % 2 == 0)
    }

    rt.x := 1
    rt.x := 2
  }

  def assertExceptionPropagated[S]
    (create: Events[Int] => Events[S], effect: () => Unit) {
    val e = new Events.Emitter[Int]
    val s = create(e)
    
    var nullptr = false
    var state = false
    var argument = false

    val h = s handle {
      case e: IllegalStateException => state = true
      case e: NullPointerException => nullptr = true
      case _ => // ignore all the rest
    }

    implicit val canLeak = Permission.newCanLeak
    val o = s onExcept {
      case e: IllegalArgumentException => argument = true
      case _ => // ignore all the rest
    }

    e.except(new NullPointerException)
    e.react(1)
    e.react(2)
    e.except(new IllegalStateException)
    effect()
    e.except(new IllegalArgumentException)

    assert(nullptr)
    assert(state)
    assert(argument)
  }

  val noEffect = () => ()

  it should "propagate the exception when filtered" in {
    assertExceptionPropagated(_.filter(_ % 2 == 0), noEffect)
  }

  it should "be mapped" in {
    val e = new Events.Emitter[Int]
    val s = e.map {
      _ + 1
    }
    val a = s foreach { case x =>
      assert(x == 2)
    }

    e react 1
  }

  it should "propagate the exception when mapped" in {
    assertExceptionPropagated(_.map(_ + 1), noEffect)
  }

  it should "be counted" in {
    val e = new Events.Emitter[Int]
    val c = e.count
    val buffer = mutable.Buffer[Int]()
    val sub = c.foreach(buffer += _)

    for (i <- 110 until 210) e react i
    assert(buffer == (1 to 100))
  }

  it should "emit once" in {
    val e = new Events.Emitter[Int]
    val s = e.once
    val check = mutable.Buffer[Int]()
    val adds = s.foreach(check += _)

    e react 1
    e react 2
    e react 3

    assert(check == Seq(1))
  }

  it should "not propagate exceptions after it emitted once" in {
    val e = new Events.Emitter[Int]
    val s = e.once
    var exceptions = 0
    val h = s.handle { case _ => exceptions += 1 }

    e.except(new RuntimeException)
    assert(exceptions == 1)
    e.except(new RuntimeException)
    assert(exceptions == 2)
    e.react(1)
    e.except(new RuntimeException)
    assert(exceptions == 2)
  }

  it should "be traversed with foreach" in {
    val e = new Events.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = e.foreach(buffer += _)

    e react 1
    e react 2
    e react 3

    buffer should equal (Seq(1, 2, 3))
  }

  it should "be scanned past" in {
    val cell = RCell(0)
    val s = cell.scanPast(List[Int]()) { (acc, x) =>
      x :: acc
    }
    val a = s foreach { case xs =>
      assert(xs.reverse == Stream.from(1).take(xs.length))
    }

    cell := 1
    cell := 2
    cell := 3
    cell := 4
    cell := 5
  }

  it should "propagate the exception when scanned past" in {
    assertExceptionPropagated(_.scanPast(0)(_ + _), noEffect)
  }

  it should "come after" in {
    val e = new Events.Emitter[Int]
    val start = new Events.Emitter[Boolean]
    val buffer = mutable.Buffer[Int]()
    val s = (e after start) foreach { case x => buffer += x }

    e react 1
    e react 2
    e react 3
    start react true
    e react 4
    e react 5
    e react 6
    start react false
    e react 7
    e react 8
    buffer should equal (Seq(4, 5, 6, 7, 8))
  }

  it should "propagate exceptions even before the first event" in {
    val start = new Events.Emitter[Unit]
    assertExceptionPropagated(_ after start, () => start.react(()))
  }

  it should "not propagate exceptions from that after the first event" in {
    val start = new Events.Emitter[Unit]
    val exceptor = new Events.Emitter[Unit]
    val a = exceptor after start

    var state = false
    var arg = false
    val h = a handle {
      case e: IllegalStateException => state = true
      case e: IllegalArgumentException => arg = true
    }

    start.except(new IllegalStateException)
    assert(state)
    start.react(())
    start.except(new IllegalArgumentException)
    assert(!arg)
  }

  it should "never come after" in {
    val e = new Events.Emitter[Int]
    val start = Events.Never[Int]
    val buffer = mutable.Buffer[Int]()
    val s = (e after start) foreach { case x => buffer += x }

    e react 1
    e react 2
    e react 3
    buffer should equal (Seq())
  }

  it should "propagate exceptions until the end event" in {
    val end = new Events.Emitter[Unit]
    val e = new Events.Emitter[Int]
    val s = e.until(end)
    var exceptions = 0
    val h = s.handle { case _ => exceptions += 1 }

    e.except(new RuntimeException)
    assert(exceptions == 1)
    end.except(new RuntimeException)
    assert(exceptions == 2)
    e.except(new RuntimeException)
    assert(exceptions == 3)
    end.react(())
    e.except(new RuntimeException)
    assert(exceptions == 3)
    end.except(new RuntimeException)
    assert(exceptions == 3)
  }

  it should "occur until" in {
    val e = new Events.Emitter[Int]
    val end = new Events.Emitter[Boolean]
    val buffer = mutable.Buffer[Int]()
    val s = (e until end) foreach { x => buffer += x }

    e react 1
    e react 2
    end react true
    e react 3
    end react false
    e react 4
    buffer should equal (Seq(1, 2))
  }

  it should "be union" in {
    val xs = new Events.Emitter[Int]
    val ys = new Events.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = (xs union ys) foreach { case x => buffer += x }

    xs react 1
    ys react 11
    xs react 2
    ys react 12
    ys react 15
    xs react 7
    buffer should equal (Seq(1, 11, 2, 12, 15, 7))
  }

  it should "propagate both exceptions to union" in {
    val xs = new Events.Emitter[Int]
    val ys = new Events.Emitter[Int]
    val zs = xs union ys

    var state = false
    var arg = false
    val h = zs handle {
      case e: IllegalStateException => state = true
      case e: IllegalArgumentException => arg = true
    }

    xs.except(new IllegalStateException)
    assert(state)
    ys.except(new IllegalArgumentException)
    assert(arg)
  }

  it should "be concat" in {
    import Permission.canBuffer
    val xs = new Events.Emitter[Int]
    val closeXs = new Events.Emitter[Unit]
    val ys = new Events.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = ((xs until closeXs) concat ys) foreach { case x => buffer += x }

    xs react 1
    ys react 11
    xs react 2
    ys react 12
    ys react 15
    xs react 7
    closeXs.react(())
    buffer should equal (Seq(1, 2, 7, 11, 12, 15))
  }


  it should "propagate both exceptions to concat" in {
    import Permission.canBuffer
    val xs = new Events.Emitter[Int]
    val ys = new Events.Emitter[Int]
    val zs = xs concat ys

    var state = false
    var arg = false
    val h = zs handle {
      case e: IllegalStateException => state = true
      case e: IllegalArgumentException => arg = true
    }

    xs.except(new IllegalStateException)
    assert(state)
    ys.except(new IllegalArgumentException)
    assert(arg)
  }

  def testSynced() {
    import Permission.canBuffer
    val xs = new Events.Emitter[Int]
    val ys = new Events.Emitter[Int]
    val synced = (xs sync ys) { _ + _ }
    val buffer = mutable.Buffer[Int]()
    val s = synced foreach { case x => buffer += x }

    for (i <- 0 until 200) xs react i
    for (j <- 200 to 51 by -1) ys react j
    buffer.size should equal (150)
    for (x <- buffer) x should equal (200)
  }

  it should "be synced" in {
    testSynced()
  }

  class Cell(var x: Int = 0)

  it should "mutate" in {
    val ms = Signal.Mutable(new Cell)
    val vals = ms.map(_.x).signal(0)
    val e = new RCell[Int](0)
    e.mutate(ms) {
      ms().x = _
    }

    e := 1
    assert(vals() == 1)
    e := 2
    assert(vals() == 2)
  }

  it should "be muxed" in {
    val cell = RCell[Events[Int]](Signal.Constant(0))
    val e1 = new Events.Emitter[Int]
    val e2 = new Events.Emitter[Int]
    val ints = cell.mux().signal(0)

    assert(ints() == 0, ints())
    cell := e1
    e1 react 10
    assert(ints() == 10, ints())
    e1 react 20
    assert(ints() == 20, ints())
    e2 react 30
    assert(ints() == 20, ints())
    cell := e2
    assert(ints() == 20, ints())
    e2 react 40
    assert(ints() == 40, ints())
    e1 react 50
    assert(ints() == 40, ints())
    e2 react 60
    assert(ints() == 60, ints())
  }

  it should "be higher-order union" in {
    val cell = RCell[Events[Int]](Signal.Constant(0))
    val e1 = new Events.Emitter[Int]
    val e2 = new Events.Emitter[Int]
    val e3 = new Events.Emitter[Int]
    val e4 = new Events.Emitter[Int]
    val closeE4 = new Events.Emitter[Unit]
    val buffer = mutable.Buffer[Int]()
    val s = cell.union() foreach { case x => buffer += x }

    e1 react -1
    e2 react -2
    e3 react -3

    cell := e1
    e1 react 1
    e1 react 11
    e2 react -22
    e3 react -33
    cell := e2
    e1 react 111
    e2 react 2
    e3 react -333
    cell := e3
    e1 react 1111
    e2 react 22
    e3 react 3
    cell := e1
    e1 react 11111
    e2 react 222
    e3 react 33
    cell := (e4 until closeE4)
    e4 react 4
    closeE4.react(())
    e4 react -44
    buffer should equal (Seq(1, 11, 111, 2, 1111, 22, 3, 11111, 222, 33, 4))
  }

  it should "be higher-order concat" in {
    import Permission.canBuffer
    val cell = RCell[Events[Int]](Signal.Constant(0))
    val e1 = new Events.Emitter[Int]
    val closeE1 = new Events.Emitter[Unit]
    val e2 = new Events.Emitter[Int]
    val closeE2 = new Events.Emitter[Unit]
    val e3 = new Events.Emitter[Int]
    val closeE3 = new Events.Emitter[Unit]
    val e4 = new Events.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = cell.concat() foreach { x => buffer += x }

    e1 react -1
    e2 react -2
    e3 react -3

    cell := e1 until closeE1
    e1 react 1
    e1 react 11
    e2 react -22
    e3 react -33
    cell := e2 until closeE2
    e1 react 111
    e2 react 2
    e3 react -333
    cell := e3 until closeE3
    e1 react 1111
    e2 react 22
    e3 react 3
    closeE1.react(())
    closeE2.react(())
    e1 react -11111
    e2 react -222
    e3 react 33
    closeE3.react(())
    e3 react -333
    cell := e4
    e4 react 4

    buffer should equal (Seq(1, 11, 111, 1111, 2, 22, 3, 33, 4))
  }

  it should "collect accurately" in {
    val e = new Events.Emitter[String]
    val evens = e collect {
      case x if x.toInt % 2 == 0 => x
    }
    val observed = mutable.Buffer[String]()
    val emitSub = evens.foreach(observed += _)

    for (i <- 0 until 100) e react i.toString

    observed should equal ((0 until 100 by 2).map(_.toString))
  }

  it should "be taken while" in {
    val e = new Events.Emitter[Int]
    val firstTen = e.takeWhile(_ < 10)
    val observed = mutable.Buffer[Int]()
    val emitSub = firstTen.foreach(observed += _)

    for (i <- 0 until 20) e react i

    observed should equal (0 until 10)

    e react 5

    observed should equal (0 until 10)
  }

  it should "be taken while until exception" in {
    val e = new Events.Emitter[Int]
    val firstTen = e.takeWhile(x => {assert(x != 5); x < 10})
    val observed = mutable.Buffer[Int]()
    val emitSub = firstTen.foreach(observed += _)

    intercept[TestFailedException] {
      for (i <- 0 until 20) e react i
    }

    observed should equal (0 until 5)
  }

  it should "react to a container" in {
    val e = new Events.Emitter[Int]
    val set = e.to[RSet[Int]]

    for (i <- 0 until 10) e react i

    for (i <- 0 until 10) set(i) should equal (true)
  }

  it should "react to unreactions" in {
    val e = new Events.Emitter[Int]
    val unreacted = e.unreacted
    var events = 0
    var excepts = 0
    var unreactions = 0
    val observations = unreacted.observe(new Reactor[Unit] {
      def react(u: Unit) {
        assert(unreactions == 0)
        events += 1
      }
      def except(t: Throwable) = excepts += 1
      def unreact() {
        assert(events == 1)
        unreactions += 1
      }
    })

    for (i <- 0 until 10) e react i

    assert(events == 0)
    assert(excepts == 0)
    assert(unreactions == 0)

    e.except(new Exception)

    assert(events == 0)
    assert(excepts == 1)
    assert(unreactions == 0)

    e.unreact()

    assert(events == 1)
    assert(excepts == 1)
    assert(unreactions == 1)
  }

  case object TestException extends Exception

  it should "throw from a foreach" in {
    var num = 0
    val e = new Events.Emitter[Int]
    val f = e.foreach(num += _)
    e.react(2)
    assert(num == 2)
    intercept[TestException.type] {
      e.except(TestException)
    }
  }

  it should "throw from ultimately" in {
    val e = new Events.Emitter[Int]
    val u = e.map[Int](_ => throw TestException).ultimately(() => {})
    intercept[TestException.type] {
      e.except(TestException)
    }
  }

  it should "throw from handle" in {
    val e = new Events.Emitter[Int]
    val h = e.handle { case _ => throw TestException }
    intercept[TestException.type] {
      e.except(new Exception)
    }
  }

  it should "throw from onReaction" in {
    implicit val canLeak = Permission.newCanLeak
    val e = new Events.Emitter[Int]
    val o = e.onReaction(new Reactor[Int] {
      def react(x: Int) {
        throw TestException
      }
      def except(t: Throwable) {
        throw TestException
      }
      def unreact() {
        throw TestException
      }
    })
    intercept[TestException.type] {
      e.react(1)
    }
    intercept[TestException.type] {
      e.except(new Exception)
    }
    intercept[TestException.type] {
      e.unreact()
    }
  }

  it should "throw from onReactUnreact" in {
    implicit val canLeak = Permission.newCanLeak
    val e = new Events.Emitter[Int]
    val o = e.onReactUnreact(x => throw TestException)(throw TestException)
    intercept[TestException.type] {
      e.react(1)
    }
    intercept[TestException.type] {
      e.unreact()
    }
  }

  it should "throw from onEvent" in {
    implicit val canLeak = Permission.newCanLeak
    val e = new Events.Emitter[Int]
    intercept[TestException.type] {
      val o = e.onEvent(x => throw TestException)
      e.react(1)
    }
    intercept[TestException.type] {
      val o = e.map(_ => throw TestException).onEvent(x => {})
      e.react(1)
    }
  }

  it should "throw from onMatch" in {
    implicit val canLeak = Permission.newCanLeak
    val e = new Events.Emitter[String]
    intercept[TestException.type] {
      val o = e.onMatch { case "1" => throw TestException }
      e.react("1")
    }
    intercept[TestException.type] {
      val o = e.map(_ => throw TestException).onMatch { case _ => }
      e.react("1")
    }
  }

  it should "throw from onExcept" in {
    implicit val canLeak = Permission.newCanLeak
    val e = new Events.Emitter[String]
    intercept[TestException.type] {
      val o = e.onExcept { case e: Exception => throw TestException }
      e.except(new Exception)
    }
  }

  it should "throw from on" in {
    implicit val canLeak = Permission.newCanLeak
    val e = new Events.Emitter[String]
    intercept[TestException.type] {
      val o = e on {}
      e.except(TestException)
    }
  }

  it should "throw from onUnreact" in {
    implicit val canLeak = Permission.newCanLeak
    val e = new Events.Emitter[String]
    intercept[TestException.type] {
      val o = e onUnreact {}
      e.except(TestException)
    }
  }

  it should "recover from errors" in {
    val e = new Events.Emitter[String]
    val recovered = e.recover({
      case e: Exception => e.getMessage
    }).to[RSet[String]]
    e.except(new Exception("false positive"))
    recovered.size should equal (1)
    recovered("false positive") should equal (true)
  }

  it should "ignore all incoming exceptions" in {
    val e = new Events.Emitter[String]
    val recovered = e.ignoreExceptions.to[RSet[String]]
    e.except(new Exception)
    recovered.size should equal (0)
  }

  it should "perform a side effect" in {
    val e = new Events.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val sub = e.effect(x => buffer += x)
    e.react(1)
    buffer should equal (Seq(1))
  }

}
