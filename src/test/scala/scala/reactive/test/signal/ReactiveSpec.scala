package scala.reactive
package test.signal



import scala.collection._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactiveSpec extends FlatSpec with ShouldMatchers {

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

  def assertExceptionPropagated[S](create: Reactive[Int] => Reactive[S], effect: () => Unit) {
    val e = new Reactive.Emitter[Int]
    val s = create(e)
    
    var nullptr = false
    var state = false
    var argument = false

    println(noEffect.getClass)
    val h = s handle {
      case e: IllegalStateException => state = true
      case e: NullPointerException => nullptr = true
    }

    implicit val canLeak = CanLeak.newCanLeak
    val o = s onExcept {
      case e: IllegalArgumentException => argument = true
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
    val e = new Reactive.Emitter[Int]
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

  it should "emit once" in {
    val e = new Reactive.Emitter[Int]
    val s = e.once
    val check = mutable.Buffer[Int]()
    val adds = s.foreach(check += _)

    e react 1
    e react 2
    e react 3

    assert(check == Seq(1))
  }

  it should "not propagate exceptions after it emitted once" in {
    val e = new Reactive.Emitter[Int]
    val s = e.once
    var exceptions = 0
    val h = s.handle(_ => exceptions += 1)

    e.except(new RuntimeException)
    assert(exceptions == 1)
    e.except(new RuntimeException)
    assert(exceptions == 2)
    e.react(1)
    e.except(new RuntimeException)
    assert(exceptions == 2)
  }

  it should "be traversed with foreach" in {
    val e = new Reactive.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = e.foreach(buffer += _)

    e react 1
    e react 2
    e react 3

    buffer should equal (Seq(1, 2, 3))
  }

  it should "propagate the exception when traversed" in {
    assertExceptionPropagated(_.foreach(x => ()), noEffect)
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
    val e = new Reactive.Emitter[Int]
    val start = new Reactive.Emitter[Boolean]
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
    val start = new Reactive.Emitter[Unit]
    assertExceptionPropagated(_ after start, () => start.react(()))
  }

  it should "not propagate exceptions from that after the first event" in {
    val start = new Reactive.Emitter[Unit]
    val exceptor = new Reactive.Emitter[Unit]
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
    val e = new Reactive.Emitter[Int]
    val start = Reactive.Never[Int]
    val buffer = mutable.Buffer[Int]()
    val s = (e after start) foreach { case x => buffer += x }

    e react 1
    e react 2
    e react 3
    buffer should equal (Seq())
  }

  it should "occur until" in {
    val e = new Reactive.Emitter[Int]
    val end = new Reactive.Emitter[Boolean]
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
    val xs = new Reactive.Emitter[Int]
    val ys = new Reactive.Emitter[Int]
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

  it should "be concat" in {
    import Permission.canBuffer
    val xs = new Reactive.Emitter[Int]
    val closeXs = new Reactive.Emitter[Unit]
    val ys = new Reactive.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = ((xs until closeXs) concat ys) foreach { case x => buffer += x }

    xs react 1
    ys react 11
    xs react 2
    ys react 12
    ys react 15
    xs react 7
    closeXs react ()
    buffer should equal (Seq(1, 2, 7, 11, 12, 15))
  }

  def testSynced() {
    import Permission.canBuffer
    val xs = new Reactive.Emitter[Int]
    val ys = new Reactive.Emitter[Int]
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
    val cell = RCell[Reactive[Int]](Signal.Constant(0))
    val e1 = new Reactive.Emitter[Int]
    val e2 = new Reactive.Emitter[Int]
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
    val cell = RCell[Reactive[Int]](Signal.Constant(0))
    val e1 = new Reactive.Emitter[Int]
    val e2 = new Reactive.Emitter[Int]
    val e3 = new Reactive.Emitter[Int]
    val e4 = new Reactive.Emitter[Int]
    val closeE4 = new Reactive.Emitter[Unit]
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
    closeE4 react ()
    e4 react -44
    buffer should equal (Seq(1, 11, 111, 2, 1111, 22, 3, 11111, 222, 33, 4))
  }

  it should "be higher-order concat" in {
    import Permission.canBuffer
    val cell = RCell[Reactive[Int]](Signal.Constant(0))
    val e1 = new Reactive.Emitter[Int]
    val closeE1 = new Reactive.Emitter[Unit]
    val e2 = new Reactive.Emitter[Int]
    val closeE2 = new Reactive.Emitter[Unit]
    val e3 = new Reactive.Emitter[Int]
    val closeE3 = new Reactive.Emitter[Unit]
    val e4 = new Reactive.Emitter[Int]
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
    closeE1 react ()
    closeE2 react ()
    e1 react -11111
    e2 react -222
    e3 react 33
    closeE3 react ()
    e3 react -333
    cell := e4
    e4 react 4

    buffer should equal (Seq(1, 11, 111, 1111, 2, 22, 3, 33, 4))
  }

  it should "collect accurately" in {
    val e = new Reactive.Emitter[String]
    val evens = e collect {
      case x if x.toInt % 2 == 0 => x
    }
    val observed = mutable.Buffer[String]()
    val emitSub = evens.foreach(observed += _)

    for (i <- 0 until 100) e react i.toString

    observed should equal ((0 until 100 by 2).map(_.toString))
  }

}
