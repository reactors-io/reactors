package io.reactors
package remote
package macros



import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context



private[reactors] class Synthesizer(val c: Context) {
  import c.universe._

  def send(x: Tree): Tree = {
    val receiver: Tree = c.macroApplication match {
      case q"$qual.this.`package`.ChannelOps[$_]($receiver).!($_)" =>
        receiver
      case tree =>
        c.error(tree.pos, s"Send must have the form: <channel> ! <event>, got: $tree")
        q"null"
    }
    val receiverBinding = TermName(c.freshName("channel"))
    val threadBinding = TermName(c.freshName("thread"))
    val eventBinding = TermName(c.freshName("event"))
    val dataBinding = TermName(c.freshName("data"))

    // println("------")
    // println(x.tpe)
    // for (m <- x.tpe.members) {
    //   println(m, m.isMethod, m.isTerm, m.isPrivate, m.isPublic)
    // }
    // x.tpe.baseClasses.foreach { cls =>
    //   println(cls, cls.isJava, cls.info.decls)
    // }

    val plan = createPlan(x.tpe)
    val sendTree = q"""
      val $receiverBinding = $receiver
      val $eventBinding = $x
      val $threadBinding = _root_.io.reactors.Reactor.currentReactorLocalThread
      if ($threadBinding != null && $threadBinding.dataCache != null) {
        val $dataBinding = $threadBinding.dataCache
        ${genMarshal(plan.marshal, q"$eventBinding", q"$dataBinding")}
      } else {
        $receiverBinding.send($eventBinding)
      }
    """
    //println(sendTree)
    sendTree
  }

  def createPlan(tpe: Type): Plan = {
    Plan(
      ReflectiveKlass(tpe.typeSymbol, None),
      ReflectiveKlass(tpe.typeSymbol, None))
  }

  def genMarshal(klass: Klass, x: Tree, data: Tree): Tree = {
    klass match {
      case MarshalableKlass(sym) =>
        q"$x.marshal($data)"
      case NormalKlass(sym, fields, exact) =>
        q"???"
      case ReflectiveKlass(sym, Some(from)) =>
        val fromFullType = from.typeSymbol.asClass.toType
        val clazz = q"_root_.scala.Predef.classOf[${fromFullType}]"
        q"""
          _root_.io.reactors.remote.RuntimeMarshaler.marshalAs($clazz, $x, $data, true)
        """
      case ReflectiveKlass(sym, None) =>
        q"""
          _root_.io.reactors.remote.RuntimeMarshaler.marshalAs(
          $x.getClass, $x, $data, false)
        """
    }
  }

  def genUnmarshal(klass: Klass, data: Tree): Tree = {
    ???
  }

  case class Plan(marshal: Klass, unmarshal: Klass)

  sealed trait Klass

  case class ReflectiveKlass(symbol: Symbol, from: Option[Type]) extends Klass

  case class MarshalableKlass(symbol: Symbol) extends Klass

  case class NormalKlass(symbol: Symbol, fields: Seq[Field], exact: Boolean)
  extends Klass

  case class Field(symbol: Symbol, klass: Klass, reflective: Boolean)
}
