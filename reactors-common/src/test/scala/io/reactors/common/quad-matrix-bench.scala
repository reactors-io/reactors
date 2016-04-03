package io.reactors
package common



import org.scalameter.api._
import org.scalameter.picklers.noPickler._
import org.scalameter.execution.invocation._



class QuadMatrixAllocationBench extends Bench.Forked[Long] {
  override def defaultConfig: Context = Context(
    exec.minWarmupRuns -> 2,
    exec.maxWarmupRuns -> 5,
    exec.independentSamples -> 1,
    verbose -> true
  )

  def measurer: Measurer[Long] = {
    val tableMeasurer = Measurer.MethodInvocationCount(
      InvocationCountMatcher.allocations(classOf[QuadMatrix.Node.Fork[String]]))
    for (table <- tableMeasurer) yield {
      table.copy(value = table.value.valuesIterator.sum)
    }
  }

  def aggregator: Aggregator[Long] = Aggregator.max

  override def reporter = Reporter.Composite(
    LoggingReporter(),
    ValidationReporter()
  )

  val matrices = for (sz <- Gen.single("size")(64)) yield {
    val quad = new QuadMatrix[String](poolSize = 512)
    for (x <- 0 until sz / 3; y <- 0 until sz / 3) {
      quad(x * 3, y * 3) = ""
    }
    quad.fillPools()
    (sz, quad)
  }

  measure method "QuadMatrix" config (
    reports.validation.predicate -> { (n: Any) => n == 0 }
  ) in {
    using(matrices) in { case (sz, quad) =>
      for (x <- 0 until sz; y <- 0 until sz) {
        quad(x, y) = ""
      }
    }
  }

}
