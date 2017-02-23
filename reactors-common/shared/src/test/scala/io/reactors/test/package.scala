package io.reactors



import java.io.File
import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import scala.sys.process._



package test {

  trait ExtendedProperties {
    val deterministicRandom = new scala.util.Random(24)

    def detChoose(low: Int, high: Int): Gen[Int] = {
      if (low > high) fail
      else {
        def draw() = {
          low + math.abs(deterministicRandom.nextInt()) % (1L + high - low)
        }
        const(0).map(_ => math.max(low, math.min(high, draw().toInt)))
      }
    }

    def detChoose(low: Double, high: Double): Gen[Double] = {
      if (low > high) fail
      else {
        def draw() = {
          low + deterministicRandom.nextDouble() * (high - low)
        }
        const(0).map(_ => math.max(low, math.min(high, draw())))
      }
    }

    def detOneOf[T](gens: Gen[T]*): Gen[T] = for {
      i <- detChoose(0, gens.length - 1)
      x <- gens(i)
    } yield x

  }

}


package object test {
  def stackTraced[T](p: =>T): T = {
    try {
      p
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }
  }

  val osName = {
    val osName = System.getProperty("os.name")
    if (osName == null) ""
    else osName.toLowerCase
  }

  def isLinuxOs: Boolean = osName.contains("nux")

  def isMacOs: Boolean = osName.contains("mac")

  def isWinOs: Boolean = osName.contains("win")

  def runXvfbTest(mainClass: String): Unit = {
    if (isLinuxOs) {
      val classpath = System.getProperty("java.class.path")
      val cmd = Seq("xvfb-run", "java", "-cp", classpath, mainClass)
      val cwd = new File(".")
      if (!sys.env.contains("TRAVIS")) assert(Process(cmd, cwd).! == 0)
      else println("Skipping UI test in Travis!")
    }
  }
}
