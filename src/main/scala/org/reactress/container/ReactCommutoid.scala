package org.reactress
package container



import scala.collection._



class ReactCommutoid[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
  (val get: S => T, val zero: T, val op: (T, T) => T)
extends ReactCatamorph[T, S] with ReactBuilder[S, ReactCommutoid[T, S]] {
  import ReactCommutoid._

  private[reactress] var root: Node[T] = null
  private[reactress] var leaves: mutable.Map[S, Leaf[T]] = null
  private val insertsEmitter = new Reactive.Emitter[S]
  private val removesEmitter = new Reactive.Emitter[S]

  def inserts: Reactive[S] = insertsEmitter

  def removes: Reactive[S] = removesEmitter

  def init(z: T) {
    root = new Empty(zero)
    leaves = mutable.Map[S, Leaf[T]]()
  }

  init(zero)

  def apply() = root.value

  def +=(v: S): Boolean = {
    if (!leaves.contains(v)) {
      val leaf = new Leaf[T](() => get(v), null)
      leaves(v) = leaf
      root = root.insert(leaf, op)
      reactAll(apply())
      insertsEmitter += v
      true
    } else false
  }

  def -=(v: S): Boolean = {
    if (leaves.contains(v)) {
      val leaf = leaves(v)
      root = leaf.remove(zero, op)
      leaves.remove(v)
      reactAll(apply())
      removesEmitter += v
      true
    } else false
  }

  def container = this

  def push(v: S): Boolean = {
    if (leaves.contains(v)) {
      val leaf = leaves(v)
      leaf.pushUp(op)
      reactAll(apply())
      true
    } else false
  }
}


object ReactCommutoid {

  def apply[@spec(Int, Long, Double) T](implicit m: Commutoid[T]) = new ReactCommutoid[T, T](v => v, m.zero, m.operator)

  sealed trait Node[@spec(Int, Long, Double) T] {
    def height: Int
    def value: T
    def parent: Inner[T]
    def parent_=(p: Inner[T]): Unit
    def pushUp(op: (T, T) => T): Unit
    def insert(leaf: Leaf[T], op: (T, T) => T): Node[T]
    def housekeep(op: (T, T) => T) {}
    def toString(indent: Int): String
    override def toString = toString(0)
  }

  class Inner[@spec(Int, Long, Double) T](var height: Int, var left: Node[T], var right: Node[T], var parent: Inner[T])
  extends Node[T] {
    private var v: T = _
    def value: T = v
    def value_=(v: T) = this.v = v
    def pushUp(op: (T, T) => T) {
      v = op(left.value, right.value)
      if (parent != null) parent.pushUp(op)
    }
    private def heightOf(l: Node[T], r: Node[T]) = 1 + math.max(l.height, r.height)
    override def housekeep(op: (T, T) => T) {
      // update height
      height = heightOf(left, right)

      // update value
      value = op(left.value, right.value)
    }
    def insert(leaf: Leaf[T], op: (T, T) => T): Node[T] = {
      if (left.height < right.height) {
        left = left.insert(leaf, op)
        left.parent = this
        value = op(left.value, right.value)
      } else {
        right = right.insert(leaf, op)
        right.parent = this
        value = op(left.value, right.value)
      }
      this.height = heightOf(left, right)
      this
    }
    def fix(op: (T, T) => T): Node[T] = {
      // check if both children are non-null
      // note that both can never be null
      def isLeft = parent.left eq this
      val result = if (left == null) {
        if (parent == null) {
          right.parent = null
          right
        } else {
          if (isLeft) parent.left = right
          else parent.right = right
          right.parent = parent
          parent.fix(op)
        }
      } else if (right == null) {
        if (parent == null) {
          left.parent = null
          left
        } else {
          if (isLeft) parent.left = left
          else parent.right = left
          left.parent = parent
          parent.fix(op)
        }
      } else {
        // check if unbalanced
        val lheight = left.height
        val rheight = right.height
        val diff = lheight - rheight
        
        // see if rebalancing is necessary
        if (diff > 1) {
          // note that this means left is inner
          val leftInner = left.asInstanceOf[Inner[T]]
          if (leftInner.left.height > leftInner.right.height) {
            // new left is the bigger left grandchild
            val nleft = leftInner.left
            nleft.parent = this
            // new right is an inner node above the right child and the smaller left grandchild
            val nright = new Inner(heightOf(leftInner.right, right), leftInner.right, right, this)
            nright.left.parent = nright
            nright.right.parent = nright
            // and we update the children
            left = nleft
            right = nright
          } else {
            // everything mirrored
            val nleft = leftInner.right
            nleft.parent = this
            val nright = new Inner(heightOf(leftInner.left, right), leftInner.left, right, this)
            nright.left.parent = nright
            nright.right.parent = nright
            left = nleft
            right = nright
          }
        } else if (diff < -1) {
          // note that this means right is inner -- everything mirrored
          val rightInner = right.asInstanceOf[Inner[T]]
          if (rightInner.left.height > rightInner.right.height) {
            val nright = rightInner.left
            nright.parent = this
            val nleft = new Inner(heightOf(rightInner.right, left), rightInner.right, left, this)
            nleft.left.parent = nleft
            nleft.right.parent = nleft
            left = nleft
            right = nright
          } else {
            val nright = rightInner.right
            nright.parent = this
            val nleft = new Inner(heightOf(rightInner.left, left), rightInner.left, left, this)
            nleft.left.parent = nleft
            nleft.right.parent = nleft
            left = nleft
            right = nright
          }
        }

        housekeep(op)

        if (parent != null) parent.fix(op)
        else this
      }

      result
    }
    def toString(indent: Int) = " " * indent + s"Inner($height, \n${left.toString(indent + 2)}, \n${right.toString(indent + 2)})"
  }

  class Leaf[@spec(Int, Long, Double) T](val get: () => T, var parent: Inner[T]) extends Node[T] {
    def height = 0
    def value = get()
    def pushUp(op: (T, T) => T) {
      if (parent != null) parent.pushUp(op)
    }
    def insert(leaf: Leaf[T], op: (T, T) => T): Node[T] = {
      val inner = new Inner(1, this, leaf, null)
      this.parent = inner
      leaf.parent = inner
      inner.value = op(this.value, leaf.value)
      inner
    }
    def remove(zero: T, op: (T, T) => T): Node[T] = {
      if (parent == null) {
        // the only value left
        new Empty(zero)
      } else {
        def isLeft = parent.left eq this
        if (isLeft) parent.left = null
        else parent.right = null
        parent.fix(op)
      }
    }
    def toString(indent: Int) = " " * indent + s"Leaf(${get()})"
  }

  class Empty[@spec(Int, Long, Double) T](val value: T) extends Node[T] {
    def height = 0
    def parent = null
    def parent_=(p: Inner[T]) = throw new IllegalStateException
    def pushUp(op: (T, T) => T) {}
    def insert(leaf: Leaf[T], op: (T, T) => T) = leaf
    def toString(indent: Int) = " " * indent + s"Empty($value)"
  }

}