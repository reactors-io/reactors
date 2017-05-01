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
    println(sendTree)
    sendTree
  }

  def createPlan(tpe: Type): Plan = {
    Plan(
      NormalKlass(tpe.typeSymbol, Nil, tpe.typeSymbol.isFinal, None),
      NormalKlass(tpe.typeSymbol, Nil, tpe.typeSymbol.isFinal, None))
  }

  def genMarshal(klass: Klass, x: Tree, data: Tree): Tree = {
    klass match {
      case MarshalableKlass(sym) =>
        q"$x.marshal($data)"
      case NormalKlass(sym, fields, exact, superclass) =>
        val staticPart = q"???"
        val dynamicPart = superclass match {
          case Some(s) => q"""
            _root_.io.reactors.remote.RuntimeMarshaler.marshalAs(
              $s, $x, $data, false)
          """
          case None => q"()"
        }
        if (exact) {
          q"""
            $staticPart
            $dynamicPart
          """
        } else {
          q"""
            if ($x.getClass ne classOf[${sym.asClass.toType}]) {
              if ($x.isInstanceOf[_root_.io.reactors.marshal.Marshalable]) {
                $x.asInstanceOf[_root_.io.reactors.marshal.Marshalable].marshal($data)
              } else {
                _root_.io.reactors.remote.RuntimeMarshaler.marshal($x, $data)
              }
            } else {
              $staticPart
              $dynamicPart
            }
          """
        }
    }
  }

  def genUnmarshal(klass: Klass, data: Tree): Tree = {
    ???
  }

  case class Plan(marshal: Klass, unmarshal: Klass)

  sealed trait Klass

  case class MarshalableKlass(symbol: Symbol) extends Klass

  case class NormalKlass(
    symbol: Symbol, fields: Seq[Field], exact: Boolean,
    superclass: Option[Type]
  ) extends Klass

  case class Field(symbol: Symbol, klass: Klass, reflective: Boolean)
}
