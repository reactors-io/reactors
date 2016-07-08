package io.reactors
package debugger



import com.github.mustachejava._
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import java.util.HashMap
import java.util.regex._
import org.apache.commons.io.IOUtils
import org.rapidoid.net.Server
import org.rapidoid.setup._
import scala.collection._



class WebDebugger(val system: ReactorSystem)
extends DebugApi with Protocol.Service {
  private val server: Server = WebDebugger.createServer(system)

  def isEnabled = false

  def eventSent[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
  }

  def eventDelivered[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
  }

  def reactorStarted(r: Reactor[_]) {
  }

  def reactorScheduled(r: Reactor[_]) {
  }

  def reactorPreempted(r: Reactor[_]) {
  }

  def reactorDied(r: Reactor[_]) {
  }

  def reactorTerminated(r: Reactor[_]) {
  }

  def connectorCreated[T](c: Connector[T]) {
  }

  def connectorSealed[T](c: Connector[T]) {
  }

  def shutdown() {
    server.shutdown()
  }
}


object WebDebugger {
  private[debugger] def createServer(system: ReactorSystem): Server = {
    val port = system.bundle.config.getInt("debug-api.port")

    def loadPage(path: String): String = {
      sealed trait NodeType
      case object Com extends NodeType
      case object Lib extends NodeType
      case object Style extends NodeType
      case object Page extends NodeType

      class Node(val path: String, val nodeType: NodeType, var content: String = "") {
        val deps = mutable.LinkedHashMap[String, Node]()
      }

      val libPattern = Pattern.compile("\\s*@@library\\((?<path>.*)\\)")
      val stylePattern = Pattern.compile("\\s*@@style\\((?<path>.*)\\)")
      val componentPattern = Pattern.compile("\\s*@@component\\((?<path>.*)\\)")
      val seen = mutable.Map[String, Node]()

      def add(n: Node): Node = {
        seen(n.path) = n
        n
      }

      def interpolate(n: Node): String = {
        val scopes = new HashMap[String, Object]
        scopes.put("url", s"${system.bundle.urlMap("reactor.udp").url.host}:$port")
        val imports = {
          val sb = new StringBuffer
          def traverse(n: Node): Unit = if (seen.contains(n.path)) {
            seen.remove(n.path)
            for ((path, d) <- n.deps) {
              traverse(d)
            }
            sb.append("\n\n")
            n.nodeType match {
              case Style =>
                sb.append("<style>\n")
                sb.append(n.content)
                sb.append("\n</style>\n")
              case Lib =>
                sb.append("<script type='text/javascript'>\n")
                sb.append(n.content)
                sb.append("\n</script>\n")
              case Com =>
                sb.append(n.content)
              case Page =>
            }
            sb.append("\n\n")
          }
          traverse(n)
          sb.toString
        }
        scopes.put("imports", imports)

        val writer = new StringWriter
        val mf = new DefaultMustacheFactory()
        val mustache =
          mf.compile(new StringReader(n.content), s"${system.name}.template")
        mustache.execute(writer, scopes)
        writer.toString
      }

      def loadPage(path: String): Node = {
        def loadString(path: String) = {
          val stream = getClass.getResourceAsStream("/" + path)
          if (stream == null) sys.error(s"Cannot find path: $path")
          IOUtils.toString(stream, "UTF-8")
        }

        def loadLibrary(path: String): Node =
          if (seen.contains(path)) seen(path)
          else add(new Node(path, Lib, loadString(path)))

        def loadStyle(path: String): Node =
          if (seen.contains(path)) seen(path)
          else add(new Node(path, Style, loadString(path)))

        def loadCom(path: String, t: NodeType = Com): Node =
          if (seen.contains(path)) seen(path) else {
            val com = add(new Node(path, t))
            val raw = loadString(path)
            val reader = new BufferedReader(new StringReader(raw))
            val sb = new StringBuffer

            var line: String = null
            while ({ line = reader.readLine(); line != null }) {
              def path(p: Pattern, txt: String): String = {
                val m = p.matcher(txt)
                if (m.matches()) m.group("path") else null
              }

              val stylepath = path(stylePattern, line)
              if (stylepath != null) {
                com.deps(stylepath) = loadStyle(stylepath)
              }

              val libpath = path(libPattern, line)
              if (libpath != null) {
                com.deps(libpath) = loadLibrary(libpath)
              }

              val compath = path(componentPattern, line)
              if (compath != null) {
                com.deps(compath) = loadCom(compath)
              }

              if (libpath == null && stylepath == null && compath == null) {
                sb.append(line).append("\n")
              }
            }
            com.content = sb.toString

            com
          }

        loadCom(path, Page)
      }

      interpolate(loadPage(path))
    }

    val debuggerPage = loadPage("io/reactors/debugger/index.html")

    val s = Setup.create(system.name)

    // attributes
    s.port(port)

    // ui routes
    s.get("/").html(debuggerPage)

    // api routes
    // TODO

    s.listen()
  }

  def main(args: Array[String]) {
    val config = ReactorSystem.customConfig("""
      debug-api = {
        name = "io.reactors.debugger.WebDebugger"
      }
    """)
    val bundle = new ReactorSystem.Bundle(Scheduler.default, config)
    val system = new ReactorSystem("web-debugger", bundle)
  }
}
