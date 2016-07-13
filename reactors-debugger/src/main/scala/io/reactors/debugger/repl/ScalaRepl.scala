package io.reactors
package debugger
package repl



import java.io.PrintWriter
import java.io.StringWriter
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.LinkedTransferQueue
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



class ScalaRepl extends Repl {
  private val lock = new AnyRef
  private val commandQueue = new LinkedTransferQueue[String]
  private val outputQueue = new LinkedTransferQueue[String]
  private val stringWriter = new StringWriter
  private val queueReader = new BufferedReader(new StringReader("")) {
    override def readLine() = commandQueue.take()
  }
  private val repl = new ILoop(Some(queueReader), new PrintWriter(stringWriter)) {
    override def processLine(line: String): Boolean = {
      val res = super.processLine(line)
      val output = stringWriter.getBuffer.toString
      stringWriter.getBuffer.setLength(0)
      outputQueue.add(output)
      res
    }
  }
  private val replThread = new Thread {
    override def run() {
      try {
        val settings = new Settings
        settings.Yreplsync.value = true
        settings.usejavacp.value = true
        repl.process(settings)
      } catch {
        case t: Throwable =>
          t.printStackTrace()
          throw t
      }
    }
  }

  {
    replThread.start()
  }

  def tpe = "Scala"

  def eval(cmd: String) = Future {
    lock.synchronized {
      commandQueue.add(cmd)
      val output = outputQueue.take()
      Repl.Result(0, output)
    }
  }

  def shutdown() {
  }
}
