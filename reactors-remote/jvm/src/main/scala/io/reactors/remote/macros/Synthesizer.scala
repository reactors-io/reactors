package io.reactors
package remote
package macros



import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context



class Synthesizer(val c: Context) {
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

    q"""
    val $eventBinding = $x
    val $receiverBinding = $receiver
    val $threadBinding = _root_.io.reactors.Reactor.currentReactorLocalThread
    if ($threadBinding != null && $threadBinding.bufferCache != null) {
      sys.error("Buffer cache optimization not implemented.")
    } else {
      $receiverBinding.send($eventBinding)
    }
    """
  }
}
