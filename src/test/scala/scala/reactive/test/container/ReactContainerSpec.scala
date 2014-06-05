package scala.reactive
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import scala.collection._



class ReactContainerSpec extends FlatSpec with ShouldMatchers {

  "A ReactContainer" should "map" in { map() }

  def map() {
    val numbers = new ReactHashSet[Long]
    val mapped = numbers.map(-_).react.to[ReactHashSet[Long]]
    
    sys.runtime.gc()

    mapped.size should equal (0)

    numbers += 1L

    mapped.size should equal (1)
    mapped(-1L) should equal (true)

    for (n <- 2 until 20) numbers += n.toLong
    for (n <- 2 until 20) mapped(-n) should equal (true)
  }

  it should "be mapped into a different container" in {
    val numbers = new ReactHashSet[Int]
    val mapped = numbers.map(2 * _).react.to[ReactHashSet[Int]]

    mapped.size should equal (0)

    numbers += 10

    mapped(10) should equal (false)
    mapped(20) should equal (true)
  }

  it should "filter" in {
    val numbers = new ReactHashSet[Int]
    val filtered = numbers.filter(_ % 2 == 0).react.to[ReactHashSet[Int]]

    filtered.size should equal (0)

    for (n <- 0 until 20) numbers += n
    for (n <- 0 until 20) filtered(n) should equal (n % 2 == 0)
  }

  def testUnionPrimitives() {
    import Permission.canBuffer
    val xs = new ReactHashSet[Int]
    val ys = new ReactHashSet[Int]
    val both = (xs union ys).react.to[ReactHashSet[Int]]
    def check(nums: Int*) {
      for (n <- nums) both(n) should equal (true)
    }

    sys.runtime.gc()

    xs += 1
    check(1)
    ys += 1
    check(1)
    xs -= 1
    check(1)
    ys -= 1
    check()
    ys += 1
    check(1)
    xs += 2
    check(1, 2)
    ys += 3
    check(1, 2, 3)
  }

  it should "union primitives" in {
    testUnionPrimitives()
  }

  it should "union references" in {
    import Permission.canBuffer
    val xs = new ReactHashSet[String]
    val ys = new ReactHashSet[String]
    val both = (xs union ys).react.to[ReactHashSet[String]]
    def check(nums: String*) {
      for (n <- nums) both(n) should equal (true)
    }

    xs += "1"
    check("1")
    ys += "1"
    check("1")
    xs -= "1"
    check("1")
    ys -= "1"
    check()
    ys += "1"
    check("1")
    xs += "2"
    check("1", "2")
    ys += "3"
    check("1", "2", "3")
  }

  it should "aggregate" in {
    val numbers = new ReactHashSet[Int]
    numbers += 1
    val sum = numbers.react.commuteFold(Commutoid(0)(_ + _))

    sum() should equal (1)

    for (n <- 2 until 20) {
      numbers += n
      sum() should equal (n * (n + 1) / 2)
    }
  }

  it should "aggregate using a typeclass" in {
    import calc.structure.setUnion
    val numbers = new ReactHashSet[Set[Int]]
    val union = numbers.react.monoidFold

    union() should equal (Set())

    for (n <- 1 until 20) {
      numbers += Set(n)
      union() should equal (Set() ++ (1 to n))
    }
  }

  it should "update the size" in {
    val numbers = ReactHashSet[Int]
    numbers += 0
    val size = numbers.react.size

    assert(size() == 1)
    numbers += 1
    assert(size() == 2)
    numbers += 2
    assert(size() == 3)
    numbers += 3
    assert(size() == 4)
    numbers -= 2
    assert(size() == 3)
    numbers += 2
    assert(size() == 4)
    numbers += 4
    assert(size() == 5)
    numbers += 4
    assert(size() == 5)
  }

  it should "foreach the elements" in {
    import scala.collection._
    val numbers = ReactHashSet[Int]
    val buffer = mutable.Buffer[Int]()
    val _ = for (x <- numbers.react) buffer += x

    numbers += 1
    numbers += 2

    buffer should equal (Seq(1, 2))

    numbers -= 2

    buffer should equal (Seq(1, 2))

    numbers += 3

    buffer should equal (Seq(1, 2, 3))
  }

  it should "count the elements" in {
    val numbers = ReactHashSet[Int]
    numbers += 0
    val even = numbers.react.count(_ % 2 == 0)

    even() should equal (1)
    numbers += 1
    even() should equal (1)
    numbers += 2
    even() should equal (2)
    numbers += 4
    even() should equal (3)
    numbers += 7
    even() should equal (3)
    numbers -= 2
    even() should equal (2)
  }

  it should "forall the elements" in {
    val numbers = ReactHashSet[Int]
    numbers += 11
    val allodd = numbers.react.forall(_ % 2 == 1)

    allodd() should equal (true)
    numbers += 17
    allodd() should equal (true)
    numbers += 12
    allodd() should equal (false)
    numbers += 15
    allodd() should equal (false)
    numbers += 20
    allodd() should equal (false)
    numbers -= 12
    allodd() should equal (false)
    numbers -= 15
    allodd() should equal (false)
    numbers -= 20
    allodd() should equal (true)
    numbers -= 11
    allodd() should equal (true)
    numbers -= 17
    allodd() should equal (true)
  }

  it should "accurately collect" in {
    val size = 512
    val table = new ReactHashMap[Int, String]
    val oks = table.collect({
      case (k, "ok") => (k, "ok")
    })
    val observed = mutable.Buffer[String]()
    val insertSub = oks.inserts.onEvent(kv => observed += kv._2)
    for (i <- 0 until size) table(i) = if (i % 2 == 0) "ok" else "notok"

    observed.size should equal (size / 2)
    for (v <- observed) v should equal ("ok")
  }

  it should "be eagerly evaluated" in {
    val size = 512
    val set = new ReactHashSet[Int]
    for (i <- 0 until size) set += i
    val mapped = set.map(_ + 1).to[ReactHashSet[Int]]

    mapped.size should equal (size)
    for (x <- mapped) set(x / 2) should equal (true)
  }

}
