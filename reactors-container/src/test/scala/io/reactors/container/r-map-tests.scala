package io.reactors
package container



import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import io.reactors.test._
import scala.collection._



class RMapCheck extends Properties("RMap") with ExtendedProperties {

  val sizes = detChoose(0, 500)

  val seeds = detChoose(0, 1000000)

  def randomInts(until: Int) = for (sz <- sizes; seed <- seeds) yield {
    val rng = new scala.util.Random(seed)
    for (i <- 0 until sz) yield rng.nextInt(until)
  }

  property("collectValue") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val table = new RHashMap[Int, String]
      val longStrings = table.collectValue {
        case s if s.length > 2 => s
      }.toMap[RHashMap[Int, String]]
      val seen = mutable.Set[Int]()
      longStrings.inserts.onEvent(seen += _)
      for (i <- 0 until sz) table(i) = i.toString
      assert(seen == (100 until sz).toSet)
      seen.clear()
      for (k <- longStrings) seen += k
      seen == (100 until sz).toSet
    }
  }

  property("RMap.apply") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      val table = new RHashMap[Int, String]
      val lengths = RMap[RFlatHashMap[Int, Int]] { m =>
        new RMap.On(table) {
          def insert(k: Int, v: String) = m(k) = v.length
          def remove(k: Int, v: String) = m.remove(k)
        }
      }
      val numbers = 0 until sz
      for (i <- numbers) table(i) = i.toString
      val seen = mutable.Map[Int, Int]()
      for (k <- lengths) seen(k) = lengths(k)
      seen == numbers.zip(numbers.map(_.toString.length)).toMap
    }
  }

}
