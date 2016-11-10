package io.reactors
package debugger



import _root_.com.github.mustachejava._
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.TimeUnit
import java.util.regex._
import org.apache.commons.io.IOUtils
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.rapidoid.gui._
import org.rapidoid.http._
import org.rapidoid.setup._
import scala.collection._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global



class WebServer(val system: ReactorSystem, webapi: WebApi) {
  val setup = WebServer.createServer(system, webapi)

  def shutdown() {
    setup.shutdown()
  }
}


object WebServer {
  private[debugger] def createServer(system: ReactorSystem, webapi: WebApi): Setup = {
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
    val s = Setup.create(system.name)

    // attributes
    s.port(port)

    // ui routes
    s.get("/").html(debuggerPage)
    s.req((req: Req) => {
      val stream = getClass.getResourceAsStream("/io/reactors/debugger/" + req.path)
      if (stream == null) sys.error(s"Cannot find path: ${req.path}")
      if (req.path.endsWith(".svg")) IOUtils.toString(stream)
      else IOUtils.toByteArray(stream)
    })

    // api routes
    s.post("/api/state").json((req: Req) => {
      val suid = req.posted.get("suid").asInstanceOf[String]
      val ts = req.posted.get("timestamp").asInstanceOf[Number].longValue
      val repluids = req.posted.get("repluids").asInstanceOf[ArrayList[String]].asScala
      asJsonNode(webapi.state(suid, ts, repluids.toList))
    })
    s.post("/api/breakpoint/add").json((req: Req) => {
      val suid = req.posted.get("suid").asInstanceOf[String]
      val pattern = req.posted.get("pattern").asInstanceOf[String]
      val tpe = req.posted.get("tpe").asInstanceOf[String]
      ???
    })
    s.post("/api/breakpoint/list").json((req: Req) => {
      val suid = req.posted.get("suid").asInstanceOf[String]
      ???
    })
    s.post("/api/breakpoint/remove").json((req: Req) => {
      val suid = req.posted.get("suid").asInstanceOf[String]
      val bid = req.posted.get("bid").asInstanceOf[Number].longValue
      ???
    })
    s.post("/api/repl/get").json((req: Req) => {
      val tpe = req.posted.get("tpe").asInstanceOf[String]
      req.async()
      webapi.replGet(tpe).onSuccess { case result =>
        req.response.json(asJsonNode(result))
        req.done()
      }
      req
    })
    s.post("/api/repl/eval").json((req: Req) => {
      val repluid = req.posted.get("repluid").asInstanceOf[String]
      val command = req.posted.get("cmd").asInstanceOf[String]
      req.async()
      webapi.replEval(repluid, command).onSuccess { case result =>
        req.response.json(asJsonNode(result))
        req.done()
      }
      req
    })
    s.post("/api/repl/close").json((req: Req) => {
      val repluid = req.posted.get("repluid").asInstanceOf[String]
      req.async()
      webapi.replClose(repluid).onSuccess { case result =>
        req.response.json(asJsonNode(result))
        req.done()
      }
      req
    })

    s
  }
}
