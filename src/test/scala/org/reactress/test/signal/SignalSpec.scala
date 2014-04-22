package org.reactress
package test.signal



import scala.collection._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class SignalSpec extends FlatSpec with ShouldMatchers {

  "A signal" should "be constant" in {
    val s = Signal.Constant(1)
    assert(s() == 1)
  }

  class ReactiveTest {
    val x = ReactCell(0)
    val y = ReactCell(0)
    val z = ReactCell(0)
    val w = ReactCell(0)
  }

  it should "be mapped" in {
    val rt = new ReactiveTest
    val s = rt.x.map {
      _ + 1
    }
    val a = s onEvent { case x =>
      assert(x == 2)
    }

    rt.x := 1
  }

  it should "be scanned past from now" in {
    val cell = ReactCell(0)
    val sum = cell.scanPastNow { (s, x) =>
      s + x
    }
    val a = sum.past2 onEvent { case t =>
      assert(t._2 - t._1 == cell())
    }

    cell := 1
    cell := 2
    cell := 3
    cell := 4
    cell := 5
  }

  it should "be diffed past" in {
    val cell = ReactCell(1)
    val diff = cell.diffPast(0)(_ - _)
    var total = 0
    val a = diff onEvent { case d =>
      total += 2
      assert(d == 2)
    }

    cell := 3
    cell := 5
    cell := 7
    cell := 9
    total should equal (8)
  }

  it should "be zipped" in {
    var updates = 0
    val rt = new ReactiveTest
    val sp1 = rt.x map { x =>
      x + 1
    } signal(1)
    val sp2 = sp1 map {
      _ + 1
    } signal(2)
    val sdiff = (sp2 zip sp1) { (x, y) =>
      updates += 1
      x - y
    }

    rt.x := rt.x() + 1
    assert(sdiff() == 1)
    rt.x := rt.x() + 1
    assert(sdiff() == 1)
  }

  it should "reflect changes" in {
    val rc = ReactCell(0)
    val buffer = mutable.Buffer[Int]()
    val subscription = rc.changes.onEvent {
      case x => buffer += x
    }

    rc := 0
    rc := 1
    rc := 2
    rc := 2
    rc := 2
    rc := 3
    rc := 3
    rc := 4
    buffer should equal (Seq(1, 2, 3, 4))
  }

  it should "be muxed as signal" in {
    val c1 = ReactCell[Int](1)
    val c2 = ReactCell[Int](2)
    val cell = ReactCell(c1)
    val ints = cell.muxSignal()

    assert(ints() == 1, ints())
    cell := c2
    assert(ints() == 2, ints())
    c2 := 3
    assert(ints() == 3, ints())
    cell := c1
    assert(ints() == 1, ints())
  }

  it should "be aggregated" in {
    val rt = new ReactiveTest
    rt.x := 1
    rt.y := 2
    rt.z := 3
    rt.w := 4
    val aggregated = Signal.Aggregate(rt.x, rt.y, rt.z, rt.w) {
      _ + _
    }

    assert(aggregated() == 10)
    rt.x := 10
    assert(aggregated() == 19)
    rt.y := 20
    assert(aggregated() == 37)
    rt.z := 30
    assert(aggregated() == 64)
    rt.w := 40
    assert(aggregated() == 100)
  }

  class Cell(var x: Int = 0)

  "A signal tuple" should "mutate" in {
    val ms = Signal.Mutable(new Cell)
    val vals = ms.map(_.x).signal(0)
    val e1 = new ReactCell[Int](0)
    val e2 = new ReactCell[Int](0)
    (e1 zip e2)(_ + _).mutate(ms) {
      ms().x = _
    }

    e1 := 1
    assert(vals() == 1)
    e2 := 2
    assert(vals() == 3)
    e1 := 3
    assert(vals() == 5)
  }

}










