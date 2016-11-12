package io.reactors



import scala.annotation.unchecked
import scala.collection._
import scala.scalajs._
import scala.scalajs.js.annotation.JSExportDescendentClasses
import scala.util.parsing.combinator._



object Platform {

  private[reactors] object HoconParser extends JavaTokenParsers {
    def configuration: Parser[Map[String, Any]] = rep(tuple) ^^ {
      case s: Seq[Map[String, Any]] @unchecked => s.foldLeft(Map[String, Any]())(_ ++ _)
    }
    def tuple: Parser[Map[String, Any]] = keyName ~ "=" ~ value ^^ {
      case name ~ _ ~ (dict: Map[String, Any] @unchecked) =>
        for ((k, v) <- dict) yield (s"$name.$k", v)
      case name ~ _ ~ v =>
        Map(name -> v)
    }
    def keyName = regex("[a-zA-Z][a-zA-Z0-9_\\-]*".r)
    def value: Parser[Any] = string | double | int | list | dictionary
    def string: Parser[String] =
      "\"" ~ regex("[ -!#-~]*".r) ~ "\"" ^^ {
        case _ ~ content ~ _ => content
      }
    def int: Parser[Int] = wholeNumber ^^ { _.toInt }
    def double: Parser[Double] = decimalNumber ^^ { _.toDouble }
    def list: Parser[Seq[Any]] = "[" ~ repsep(value, ",") ~ "]" ^^ {
      case _ ~ values ~ _ => values
    }
    def dictionary: Parser[Map[String, Any]] = "{" ~ configuration ~ "}" ^^ {
      case _ ~ config ~ _ => config
    }

    def simpleParse(s: String): Map[String, Any] = {
      parseAll(configuration, s.trim) match {
        case Success(m, _) => m
        case _ => sys.error(s"Cannot parse '$s'.")
      }
    }
  }

  private[reactors] class SimpleConfiguration(val paths: Map[String, Any])
  extends Configuration {
    def int(path: String): Int = paths(path).asInstanceOf[Int]
    def string(path: String): String = paths(path).asInstanceOf[String]
    def double(path: String): Double = paths(path).asInstanceOf[Double]
    def list(path: String): Seq[Configuration] = {
      val values = paths(path).asInstanceOf[Seq[Map[String, Any]]]
      values.map(v => new SimpleConfiguration(v))
    }
    def children(path: String): Seq[Configuration] = {
      val prefix = path + "."
      def isDict(p: String): Boolean = {
        p.startsWith(prefix) && p.substring(prefix.length).indexOf('.') != -1
      }
      paths.toSeq.collect {
        case (p, obj) if isDict(p) =>
          val parts = p.substring(prefix.length).split("\\.")
          val group = parts(0)
          val nkey = parts(1)
          (group, (nkey, obj))
      }.groupBy { case (p, _) => p }.toMap.map {
        case (group, elems) =>
          (group, elems.map { case (_, (nkey, obj)) => (nkey, obj) }.toMap)
      }.values.toList.map(m => new SimpleConfiguration(m))
    }
    def withFallback(other: Configuration): Configuration = {
      val newPaths = mutable.Map[String, Any](paths.toSeq: _*)
      for ((p, obj) <- other.asInstanceOf[SimpleConfiguration].paths) {
        if (!newPaths.contains(p)) newPaths(p) = obj
      }
      new SimpleConfiguration(newPaths)
    }
  }

  private[reactors] val configurationFactory = new Configuration.Factory {
    def parse(s: String) = new SimpleConfiguration(HoconParser.simpleParse(s))
    def empty = new SimpleConfiguration(Map())
  }

  private[reactors] val machineConfiguration = s"""
    system = {
      num-processors = ${1}
    }
  """

  private[reactors] val defaultConfiguration = """
    pickler = "io.reactors.pickle.NoPickler"
    remote = {
      default-schema = "local"
      transports = [
        {
          schema = "local"
          transport = "io.reactors.remote.LocalTransport"
          host = ""
          port = 0
        },
        {
          schema = "datagram"
          transport = "io.reactors.remote.NodeJSDatagramTransport"
          host = "localhost"
          port = 17771
        }
      ]
    }
    debug-api = {
      name = "io.reactors.debugger.ZeroDebugApi"
    }
    error-handler = {
      name = "io.reactors.DefaultErrorHandler"
    }
    scheduler = {
      spindown = {
        initial = 0
        min = 0
        max = 0
        cooldown-rate = 8
        mutation-rate = 0.15
        test-threshold = 32
        test-iterations = 3
      }
      default = {
        budget = 50
        postschedule-count = 0
      }
    }
    system = {
      net = {
        parallelism = 1
      }
    }
  """

  private[reactors] def registerDefaultSchedulers(b: ReactorSystem.Bundle): Unit = {
    b.registerScheduler(JsScheduler.Key.default, JsScheduler.default)
  }

  private[reactors] lazy val defaultScheduler = JsScheduler.default

  private[reactors] def inetAddress(host: String, port: Int) = ???

  private[reactors] object Reflect {
    def instantiate[T](clazz: Class[T], args: Seq[Any]): T = {
      instantiate(clazz.getName, args)
    }

    def instantiate[T](className: String, args: Seq[Any]): T = {
      val ctor = (js.Dynamic.global /: className.split("\\.")) {
        (prev, part) =>
        prev.selectDynamic(part)
      }
      js.Dynamic.newInstance(ctor)(args.asInstanceOf[Seq[js.Any]]: _*).asInstanceOf[T]
    }
  }

  @scala.scalajs.js.annotation.JSExportDescendentClasses(true)
  trait Reflectable {
  }

  private[reactors] class SnapshotMap[K, V] extends mutable.HashMap[K, V] {
    def replace(k: K, ov: V, nv: V): Boolean = this.get(k) match {
      case Some(v) if v == ov =>
        this(k) = nv
        true
      case _ =>
        false
    }

    def putIfAbsent(k: K, v: V): Option[V] = this.get(k) match {
      case Some(ov) =>
        Some(ov)
      case None =>
        this(k) = v
        None
    }

    def snapshot: Map[K, V] = {
      val m = mutable.Map[K, V]()
      for ((k, v) <- this) m(k) = v
      m
    }
  }

  private[reactors] def newSnapshotMap[K, V] = new SnapshotMap[K, V]
}
