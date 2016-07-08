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
      val libs = mutable.Map[String, String]()
      val styles = mutable.Map[String, String]()
      val components = mutable.Map[String, String]()
      val libOrder = mutable.Buffer[String]()
      val styleOrder = mutable.Buffer[String]()
      val componentOrder = mutable.Buffer[String]()
      val libPattern = Pattern.compile("\\s*@@library\\((?<path>.*)\\)")
      val stylePattern = Pattern.compile("\\s*@@style\\((?<path>.*)\\)")
      val componentPattern = Pattern.compile("\\s*@@component\\((?<path>.*)\\)")

      def interpolate(raw: String): String = {
        val scopes = new HashMap[String, Object]
        scopes.put("url", s"${system.bundle.urlMap("reactor.udp").url.host}:$port")
        val imports = {
          val sb = new StringBuffer
          def insert(
            before: String, after: String,
            chunks: mutable.Map[String, String], order: Seq[String]
          ) {
            for (path <- order.reverse) {
              if (chunks.contains(path)) {
                sb.append(before)
                sb.append("\n")
                sb.append(chunks(path))
                sb.append("\n")
                sb.append(after)
                sb.append("\n\n")
                chunks.remove(path)
              }
            }
          }
          insert("<script type='text/javascript'>", "</script>", libs, libOrder)
          insert("<style>", "</style>", styles, styleOrder)
          insert("", "", components, componentOrder)
          sb.toString
        }
        scopes.put("imports", imports)

        val writer = new StringWriter
        val mf = new DefaultMustacheFactory()
        val mustache = mf.compile(new StringReader(raw), s"${system.name}.template")
        mustache.execute(writer, scopes)
        writer.toString
      }

      def loadPage(path: String): String = {
        def loadString(path: String) = {
          val stream = getClass.getResourceAsStream("/" + path)
          if (stream == null) sys.error(s"Cannot find path: $path")
          IOUtils.toString(stream, "UTF-8")
        }

        def loadLibrary(path: String) = loadString(path)

        def loadStyle(path: String): String = loadString(path)

        def loadComponent(path: String): String = {
          val text = loadString(path)
          val sb = new StringBuffer
          val reader = new BufferedReader(new StringReader(text))
          var line: String = null
          while ({ line = reader.readLine(); line != null }) {
            def path(p: Pattern, txt: String): String = {
              val m = p.matcher(txt)
              if (m.matches()) m.group("path") else null
            }

            val stylepath = path(stylePattern, line)
            if (stylepath != null) {
              styleOrder += stylepath
              if (!styles.contains(stylepath)) styles(stylepath) = loadStyle(stylepath)
            }

            val libpath = path(libPattern, line)
            if (libpath != null) {
              libOrder += libpath
              if (!libs.contains(libpath)) libs(libpath) = loadLibrary(libpath)
            }

            val compath = path(componentPattern, line)
            if (compath != null) {
              componentOrder += compath
              if (!components.contains(compath))
                components(compath) = loadComponent(compath)
            }

            if (libpath == null && stylepath == null && compath == null) {
              sb.append(line).append("\n")
            }
          }
          sb.toString
        }

        val expanded = loadComponent(path)
        expanded
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
