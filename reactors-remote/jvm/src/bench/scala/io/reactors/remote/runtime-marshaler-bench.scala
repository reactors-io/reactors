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
    exec.minWarmupRuns -> 180,
    exec.maxWarmupRuns -> 260,
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

//  @gen("noSizes")
//  @benchmark("io.reactors.remote.runtime-marshaler")
//  @curve("serialize-final-single-field-class")
//  def serializeFinalSingleFieldClass(bufferSize: Int) = {
//    var i = 0
//    val baos = new ByteArrayOutputStream()
//    val oos = new ObjectOutputStream(baos)
//    while (i < repetitions) {
//      val obj = new SingleField(i)
//      oos.writeObject(obj)
//      i += 1
//    }
//  }
//
//  @gen("bufferSizes")
//  @benchmark("io.reactors.remote.runtime-marshaler")
//  @curve("write-final-single-field-class")
//  def writeFinalSingleFieldClass(bufferSize: Int) = {
//    var i = 0
//    var data: Data = new Data.Linked(bufferSize, bufferSize)
//    val cell = new Cell[Data](data)
//    val desc = Platform.Reflect.descriptorOf(classOf[SingleField])
//    while (i < repetitions) {
//      val obj = new SingleField(11)
//      val v = obj.x
//      if (data.remainingWriteSize < 4) data = data.flush(4)
//      val pos = data.endPos
//      data(pos + 0) = ((v & 0x000000ff) >>> 0).toByte
//      data(pos + 1) = ((v & 0x0000ff00) >>> 8).toByte
//      data(pos + 2) = ((v & 0x00ff0000) >>> 16).toByte
//      data(pos + 3) = ((v & 0xff000000) >>> 24).toByte
//      data.endPos += 4
//      if (data.remainingWriteSize < 4) data = data.flush(4)
//      val v1 = 1
//      val pos1 = data.endPos
//      data(pos1 + 0) = ((v1 & 0x000000ff) >>> 0).toByte
//      data(pos1 + 1) = ((v1 & 0x0000ff00) >>> 8).toByte
//      data(pos1 + 2) = ((v1 & 0x00ff0000) >>> 16).toByte
//      data(pos1 + 3) = ((v1 & 0xff000000) >>> 24).toByte
//      data.endPos += 4
//      i += 1
//    }
//    data
//  }

  @gen("bufferSizes")
  @benchmark("io.reactors.remote.runtime-marshaler")
  @curve("marshal-final-single-field-class")
  def marshalFinalSingleFieldClass(bufferSize: Int) = {
    var i = 0
    var data: Data = new Data.Linked(bufferSize, bufferSize)
    val cell = new Cell[Data](data)
    val desc = Platform.Reflect.descriptorOf(classOf[SingleField])
    while (i < repetitions) {
      val obj = new SingleField(11)
      data = RuntimeMarshaler.marshalAs(desc, obj, data, false)
      i += 1
    }
    data
  }
}


final class SingleField(val x: Int) extends Marshalee
