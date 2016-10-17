package io.reactors



import io.reactors.protocol._
import org.coroutines._
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context



package object direct {
  private[direct] def runDirectThunk(cu: Any): Unit = {
    val c = cu.asInstanceOf[Coroutine._0[IVar[Any], Any]]
    val frame = call(c())
    def loop() {
      if (frame.pull) {
        frame.value.on(loop)
      }
    }
    loop()
  }

  def macroDirectThunk(c: Context)(body: c.Tree): c.Tree = {
    import c.universe._
    val coroutineName = TermName(c.freshName("c"))
    q"""
      val $coroutineName: Any = _root_.org.coroutines.coroutine(() => $body)
      _root_.io.reactors.direct.runDirectThunk($coroutineName)
    """
  }

  def behavior(body: =>Unit): Unit = macro macroDirectThunk

  def macroDirectProto[T: c.WeakTypeTag](c: Context)(
    body: c.Tree
  ): c.Tree = {
    import c.universe._
    val coroutineName = TermName(c.freshName("c"))
    val t = q"""
      val $coroutineName: Any = _root_.org.coroutines.coroutine(() => $body)
      _root_.io.reactors.Reactor[${weakTypeTag[T]}] { self =>
        _root_.io.reactors.direct.runDirectThunk($coroutineName)
      }
    """
    t
  }

  implicit class ReactorCoroutineOps(val r: Reactor.type) extends AnyVal {
    def direct[T](body: =>Unit): Proto[Reactor[T]] = macro macroDirectProto[T]
  }

  private[direct] val receiveInstance: Coroutine._1[Events[Any], IVar[Any], Any] =
    coroutine { (events: Events[Any]) =>
      val ivar = events.toIVar
      yieldval(ivar)
      ivar()
    }

  /** Suspends execution of the reactor until the event stream produces an event.
   *
   *  This combinator can only be used inside direct reactors. The coroutine it
   *  returns is meant to be invoked from another coroutine or a direct context,
   *  but not started with the `call` combinator.
   *
   *  Example:
   *
   *  {{{
   *  system.spawn(Reactor.direct[String] {
   *    val x = Reactor.self[String].main.events.receive()
   *    println(x)
   *  })
   *  }}}
   *
   *  @return    A coroutine that yields an `onEvent` function, and returns the event.
   */
  def receive[T] = receiveInstance.asInstanceOf[Coroutine._1[Events[T], IVar[T], T]]

  def backpressureSend[T: c.WeakTypeTag](c: Context)(x: c.Tree): c.Tree = {
    import c.universe._
    val q"direct.this.`package`.BackpressureLinkOps[$_](${link: Tree})" = c.prefix.tree
    val evaledLink = Ident(c.fresh("link"))
    val evaledX = Ident(c.fresh("x"))

    val t = q"""
      val $evaledLink = $link
      val $evaledX = $x
      if (!$evaledLink.trySend($evaledX)) {
        _root_.io.reactors.direct.receive[Boolean]($evaledLink.available)
        $evaledLink.trySend($evaledX)
        true
      } else {
        true
      }
    """
    t
  }

  implicit class BackpressureLinkOps[T](val link: Backpressure.Link[T]) {
    def !(x: T): Unit = macro backpressureSend[T]
  }
}
