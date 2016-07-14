package io.reactors
package debugger
package repl



import java.io.PrintWriter
import java.io.StringWriter
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.LinkedTransferQueue
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



class ScalaRepl(val system: ReactorSystem) extends Repl {
  private val lock = new AnyRef
  private val startedPromise = Promise[Boolean]()
  private val commandQueue = new LinkedTransferQueue[String]
  private val outputQueue = new LinkedTransferQueue[Repl.Result]
  class ExtractableWriter {
    val stringWriter = new StringWriter
    val printWriter = new PrintWriter(stringWriter)
    val outputStream = new OutputStream {
      def write(b: Int) {
        stringWriter.write(b)
      }
    }
    def extract(): String = {
      var result = stringWriter.getBuffer.toString
      stringWriter.getBuffer.setLength(0)
      result = result.split("\n")
        .map(_.replaceFirst("scala> ", ""))
        .filter(_ != "     | ")
        .filter(_.trim() != "")
        .mkString("\n")
      result
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
  private val repl = new ILoop(Some(queueReader), extractableWriter.printWriter) {
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
        Console.withOut(extractableWriter.outputStream) {
          Console.withErr(extractableWriter.outputStream) {
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
