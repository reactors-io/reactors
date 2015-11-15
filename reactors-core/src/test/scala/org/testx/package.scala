package org



import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._



package testx {

  trait ExtendedProperties {
    val deterministicRandom = new scala.util.Random(24)

    def detChoose(low: Int, high: Int): Gen[Int] = {
      if (low > high) fail
      else {
        def draw() = {
          low + math.abs(deterministicRandom.nextInt()) % (1L + high - low)
        }
        value(0).map(_ => math.max(low, math.min(high, draw().toInt)))
      }
    }

    def detChoose(low: Double, high: Double): Gen[Double] = {
      if (low > high) fail
      else {
        def draw() = {
          low + deterministicRandom.nextDouble() * (high - low)
        }
        value(0).map(_ => math.max(low, math.min(high, draw())))
      }
    }

    def detOneOf[T](gens: Gen[T]*): Gen[T] = for {
      i <- detChoose(0, gens.length - 1)
      x <- gens(i)
    } yield x

  }

}


package object testx {

  def stackTraced[T](p: =>T): T = {
    try {
      p
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }
  }

}
