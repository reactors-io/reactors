package io.reactors
package remote



import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import io.reactors.common.Cell
import io.reactors.marshal.Marshalee
import org.scalameter.api._
import org.scalameter.japi.JBench
import org.scalameter.picklers.Implicits._



class RuntimeMarshalerBench extends JBench.OfflineReport {
  override def defaultConfig = Context(
    exec.minWarmupRuns -> 80,
    exec.maxWarmupRuns -> 160,
    exec.benchRuns -> 72,
    exec.independentSamples -> 1,
    verbose -> true
  )

  override def reporter = Reporter.Composite(
    new RegressionReporter(tester, historian),
    new MongoDbReporter[Double]
  )

  val noSizes = Gen.single("bufferSizes")(0)

  val bufferSizes = Gen.single("bufferSizes")(250)

  val repetitions = 100000

  @transient lazy val system = new ReactorSystem("reactor-bench")

  @gen("noSizes")
  @benchmark("io.reactors.remote.runtime-marshaler")
  @curve("serialize-final-single-field-class")
  def serializeFinalSingleFieldClass(bufferSize: Int) = {
    var i = 0
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    while (i < repetitions) {
      val obj = new SingleField(i)
      oos.writeObject(obj)
      i += 1
    }
  }

  @gen("bufferSizes")
  @benchmark("io.reactors.remote.runtime-marshaler")
  @curve("marshal-final-single-field-class")
  def marshalFinalSingleFieldClass(bufferSize: Int) = {
    var i = 0
    val data = new Data.Linked(bufferSize, bufferSize)
    val cell = new Cell[Data](data)
    while (i < repetitions) {
      val obj = new SingleField(i)
      RuntimeMarshaler.marshal(obj, data)
      i += 1
    }
    data
  }
}


final class SingleField(val x: Int) extends Marshalee
