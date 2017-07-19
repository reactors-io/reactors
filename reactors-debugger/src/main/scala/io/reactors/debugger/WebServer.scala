package io.reactors
package debugger



import _root_.com.github.mustachejava._
import io.reactors.http._
import io.reactors.json._
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.TimeUnit
import java.util.regex._
import org.apache.commons.io.IOUtils
import org.rapidoid.http._
import org.rapidoid.setup._
import scalajson.ast._
import scala.collection._
import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._



class WebServer(webapi: WebApi) extends Reactor[WebServer.Command] {
  val http = WebServer.createServer(system, webapi)

  def shutdown() {
    http.shutdown()
  }
}


object WebServer {
  sealed trait Command

  case object Shutdown extends Command

  private[debugger] def createServer(
    system: ReactorSystem, webapi: WebApi
  ): Http.Adapter = {
    val port = system.bundle.config.int("debug-api.port")

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
        scopes.put("reactor-system.url",
          s"${system.bundle.urlMap("udp").url.host}:$port")
        scopes.put("reactor-system.version", "0.7")
        scopes.put("debugger-ui.configuration", "{}")
        scopes.put("debugger-ui.plugins", "<!-- No plugins -->")
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
        scopes.put("debugger-ui.imports", imports)

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

        def absorbImports(n: Node, raw: String): String = {
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
              n.deps(stylepath) = loadStyle(stylepath)
            }

            val libpath = path(libPattern, line)
            if (libpath != null) {
              n.deps(libpath) = loadLibrary(libpath)
            }

            val compath = path(componentPattern, line)
            if (compath != null) {
              n.deps(compath) = loadCom(compath)
            }

            if (libpath == null && stylepath == null && compath == null) {
              sb.append(line).append("\n")
            }
          }

          sb.toString
        }

        def loadLibrary(path: String): Node =
          if (seen.contains(path)) seen(path)
          else add(new Node(path, Lib, loadString(path)))

        def loadStyle(path: String): Node =
          if (seen.contains(path)) seen(path) else {
            val style = add(new Node(path, Style))
            val raw = loadString(path)
            style.content = absorbImports(style, raw)
            style
          }

        def loadCom(path: String, t: NodeType = Com): Node =
          if (seen.contains(path)) seen(path) else {
            val com = add(new Node(path, t))
            val raw = loadString(path)
            com.content = absorbImports(com, raw)
            com
          }

        loadCom(path, Page)
      }

      interpolate(loadPage(path))
    }

    val debuggerPage = loadPage("io/reactors/debugger/index.html")
    val s = Reactor.self.system.service[Http].at(port)

    // ui routes
    s.html("/") { req =>
      debuggerPage
    }
    s.default { req =>
      val stream = getClass.getResourceAsStream("/io/reactors/debugger/" + req.uri)
      if (stream == null) sys.error(s"Cannot find path: ${req.uri}")
      if (req.uri.endsWith(".svg")) ("text/plain", IOUtils.toString(stream))
      else ("application/octet-stream", stream)
    }

    // api routes
    s.json("/api/state") { req =>
      val values = req.input.asJson.asJObject.value
      val suid = values("suid").asString
      val ts = values("timestamp").asLong
      val repluids = values("repluids").asList(_.asString)
      webapi.state(suid, ts, repluids.toList).jsonString
    }
    s.json("/api/breakpoint/add") { req =>
      val values = req.input.asJson.asJObject.value
      val suid = values("suid").asString
      val ts = values("pattern").asString
      val tpe = values("tpe").asString
      ???
    }
    s.json("/api/breakpoint/list") { req =>
      val values = req.input.asJson.asJObject.value
      val suid = values("suid").asString
      ???
    }
    s.json("/api/breakpoint/remove") { req =>
      val values = req.input.asJson.asJObject.value
      val suid = values("suid").asString
      val bid = values("bid").asLong
      ???
    }
    s.json("/api/repl/get") { req =>
      val values = req.input.asJson.asJObject.value
      val tpe = values("tpe").asString
      val result = webapi.replGet(tpe)
      Await.result(result, Duration.Inf).jsonString
    }
    s.json("/api/repl/eval") { req =>
      val values = req.input.asJson.asJObject.value
      val repluid = values("repluid").asString
      val command = values("cmd").asString
      val result = webapi.replEval(repluid, command)
      Await.result(result, Duration.Inf).jsonString
    }
    s.json("/api/repl/close") { req =>
      val values = req.input.asJson.asJObject.value
      val repluid = values("repluid").asString
      val result = webapi.replClose(repluid)
      Await.result(result, Duration.Inf).jsonString
    }

    s
  }
}
