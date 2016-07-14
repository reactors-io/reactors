package io.reactors
package debugger
package repl



import java.io.BufferedReader
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.util.concurrent.LinkedTransferQueue
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



class ScalaRepl(val system: ReactorSystem) extends Repl {
  private val lock = new AnyRef
  private val startedPromise = Promise[Boolean]()
  private val pendingOutputQueue = new LinkedTransferQueue[String]
  private val commandQueue = new LinkedTransferQueue[String]
  private val outputQueue = new LinkedTransferQueue[Repl.Result]
  class ExtractableWriter {
    val localStringWriter = new StringWriter
    val localPrintWriter = new PrintWriter(localStringWriter)
    val globalOutputStream = new OutputStream {
      def write(b: Int) {
        System.out.write(b)
        localStringWriter.write(b)
      }
    }
    val globalPrintStream = new PrintStream(globalOutputStream)
    def extractPending(): String = {
      val sb = new StringBuilder
      while (!pendingOutputQueue.isEmpty) sb.append(pendingOutputQueue.take())
      sb.toString
    }
    def extract(): String = {
      var output = localStringWriter.getBuffer.toString
      localStringWriter.getBuffer.setLength(0)
      output = output.split("\n")
        .map(_.replaceFirst("scala> ", ""))
        .filter(_ != "     | ")
        .filter(_.trim() != "")
        .mkString("\n")
      val pendingOutput = extractPending()
      pendingOutput + output
    }
  }
  private val extractableWriter = new ExtractableWriter
  class QueueReader extends BufferedReader(new StringReader("")) {
    var continueMode = false
    override def readLine() = {
      if (continueMode)
        outputQueue.add(Repl.Result(0, extractableWriter.extract(), "     | ", true))
      commandQueue.take()
    }
  }
  private val queueReader = new QueueReader
  private val repl = new ILoop(Some(queueReader), extractableWriter.localPrintWriter) {
    override def createInterpreter() {
      super.createInterpreter()
      intp.beQuietDuring {
        intp.bind("system", "io.reactors.ReactorSystem", system)
      }
    }
    override def processLine(line: String): Boolean = {
      val res = try {
        queueReader.continueMode = true
        super.processLine(line)
      } finally {
        queueReader.continueMode = false
      }
      val output = extractableWriter.extract()
      outputQueue.add(Repl.Result(0, output, "scala> ", false))
      startedPromise.trySuccess(true)
      res
    }
  }
  private val replThread = new Thread(s"reactors-io.${system.name}.repl-thread") {
    override def run() {
      try {
        Console.withOut(extractableWriter.globalPrintStream) {
          Console.withErr(extractableWriter.globalPrintStream) {
            val settings = new Settings
            settings.Yreplsync.value = true
            settings.usejavacp.value = true
            repl.process(settings)
          }
        }
      } catch {
        case t: Throwable =>
          t.printStackTrace()
          throw t
      }
    }
  }

  {
    // Add import command, which also triggers output of splash message.
    commandQueue.add("import io.reactors._")
    // Start REPL thread.
    replThread.start()
  }

  def tpe = "Scala"

  def started = startedPromise.future

  def eval(cmd: String) = Future {
    lock.synchronized {
      commandQueue.add(cmd)
      val result = outputQueue.take()
      result
    }
  }

  def log(x: Any) = {
    pendingOutputQueue.add(x.toString)
  }

  def flush(): String = {
    lock.synchronized {
      val lines = mutable.Buffer[String]()
      while (outputQueue.peek != null) {
        lines += outputQueue.poll().output
      }
      lines.mkString("\n")
    }
  }

  def shutdown() {
  }
}
