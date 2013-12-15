package org.reactress
package test.signal



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
    val a = s onValue { x =>
      assert(x == 2)
    }

    rt.x := 1
  }

  it should "be filtered" in {
    val rt = new ReactiveTest
    val s = rt.x.filter {
      _ % 2 == 0
    }
    val a = s onValue { x =>
      assert(x % 2 == 0)
    }

    rt.x := 1
    rt.x := 2
  }

  it should "be folded past" in {
    val rt = new ReactiveTest
    val s = rt.x.foldPast(List[Int]()) { (acc, x) =>
      x :: acc
    }
    val a = s onValue { xs =>
      assert(xs.reverse == Stream.from(1).take(xs.length))
    }

    rt.x := 1
    rt.x := 2
    rt.x := 3
    rt.x := 4
    rt.x := 5
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

  it should "be muxed" in {
    val s = ReactCell[Reactive[Int]](Signal.Constant(0))
    val e1 = new Reactive.Emitter[Int]
    val e2 = new Reactive.Emitter[Int]
    val ints = s.mux.signal(0)

    assert(ints() == 0)
    s := e1
    e1 += 10
    assert(ints() == 10)
    e1 += 20
    assert(ints() == 20)
    e2 += 30
    assert(ints() == 20)
    s := e2
    assert(ints() == 20)
    e2 += 40
    assert(ints() == 40)
    e1 += 50
    assert(ints() == 40)
    e2 += 60
    assert(ints() == 60)
  }

  class Cell(var x: Int = 0)

  it should "mutate" in {
    val ms = Signal.Mutable(new Cell)
    val vals = ms.map(_.x).signal(0)
    val e = new ReactCell[Int](0)
    e.mutate(ms) {
      _().x = _
    }

    e := 1
    assert(vals() == 1)
    e := 2
    assert(vals() == 2)
  }

  "A signal tuple" should "mutate" in {
    val ms = Signal.Mutable(new Cell)
    val vals = ms.map(_.x).signal(0)
    val e1 = new ReactCell[Int](0)
    val e2 = new ReactCell[Int](0)
    (e1 zip e2)(_ + _).mutate(ms) {
      _().x = _
    }

    e1 := 1
    assert(vals() == 1)
    e2 := 2
    assert(vals() == 3)
    e1 := 3
    assert(vals() == 5)
  }

}










