package org.reactress
package container



import scala.collection._



class ReactAggregate[@spec(Int, Long, Double) T](val zero: T)(private val op: (T, T) => T)
extends Signal[T] with ReactContainer[Signal[T], ReactAggregate[T]] with ReactBuilder[Signal[T], ReactAggregate[T]] {
  private var tree: ReactAggregate.Tree[T, Signal[T]] = null
  private var insertsEmitter: Reactive.Emitter[Signal[T]] = null
  private var removesEmitter: Reactive.Emitter[Signal[T]] = null

  def init(z: T) {
    tree = new ReactAggregate.Tree(zero)(op, onTick, {
      s => s onTick {
        tree.pushUp(s)
      }
    })
    insertsEmitter = new Reactive.Emitter[Signal[T]]
    removesEmitter = new Reactive.Emitter[Signal[T]]
  }

  init(zero)

  def builder: ReactBuilder[Signal[T], ReactAggregate[T]] = this

  def result = this

  private def onTick() {
    reactAll(apply())
  }

  def apply(): T = tree.root.value
  
  def +=(s: Signal[T]): this.type = {
    // update tree
    tree += s
    
    // emit insertion event
    insertsEmitter += s

    // notify subscribers
    onTick()

    this
  }

  def -=(s: Signal[T]): this.type = {
    // update tree
    tree -= s
    
    // emit removal event
    removesEmitter += s
    
    // notify subscribers
    onTick()

    this
  }

  def inserts: Reactive[Signal[T]] = insertsEmitter

  def removes: Reactive[Signal[T]] = removesEmitter

  override def toString = s"ReactAggregate(${apply()})"
}


object ReactAggregate {
  def by[@spec(Int, Long, Double) T](zero: T)(op: (T, T) => T) = new ReactAggregate[T](zero)(op)

  def apply[@spec(Int, Long, Double) T]()(implicit cm: CommuteMonoid[T]) = new ReactAggregate(cm.zero)(cm.operator)

  implicit def factory[@spec(Int, Long, Double) T](implicit cm: CommuteMonoid[T]) = new ReactBuilder.Factory[ReactAggregate[_], Signal[T], ReactAggregate[T]] {
    def create() = new ReactAggregate[T](cm.zero)(cm.operator)
  }

  // TODO refactor to couple aggregates more tightly

  class Tree[@spec(Int, Long, Double) T, V <: Value[T]](val zero: T)(val op: (T, T) => T, val onTick: () => Unit, val subscribe: V => Reactive.Subscription) {
    private[reactress] var _root: Node[T] = null
    private[reactress] var leaves: mutable.Map[Value[T], (Leaf[T], Reactive.Subscription)] = null

    def init(z: T) {
      _root = new Empty(zero)
      leaves = mutable.Map[Value[T], (Leaf[T], Reactive.Subscription)]()
    }

    init(zero)

    def root = _root

    def +=(v: V) = {
      val leaf = new Leaf(v, null)

      _root = _root.insert(leaf, op)

      leaves(v) = (leaf, subscribe(v))
      onTick()
    }

    def -=(v: V) = {
      val leafsub = leaves(v)

      _root = leafsub._1.remove(zero, op)

      leafsub._2.unsubscribe()
      leaves.remove(v)
      onTick()
    }

    def pushUp(v: V) = {
      val leafsub = leaves(v)

      leafsub._1.refresh(op)

      onTick()
    }

  }

  sealed trait Node[@spec(Int, Long, Double) T] {
    def height: Int
    def value: T
    def parent: Inner[T]
    def parent_=(p: Inner[T]): Unit
    def refresh(op: (T, T) => T): Unit
    def insert(leaf: Leaf[T], op: (T, T) => T): Node[T]
    def toString(indent: Int): String
    override def toString = toString(0)
  }

  class Inner[@spec(Int, Long, Double) T](var height: Int, var left: Node[T], var right: Node[T], var parent: Inner[T])
  extends Node[T] {
    private var v: T = _
    def value: T = v
    def value_=(v: T) = this.v = v
    def refresh(op: (T, T) => T) {
      v = op(left.value, right.value)
      if (parent != null) parent.refresh(op)
    }
    private def heightOf(l: Node[T], r: Node[T]) = 1 + math.max(l.height, r.height)
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
          parent.fix(op) // TODO error! remove this
        }
      } else if (right == null) {
        if (parent == null) {
          left.parent = null
          left
        } else {
          if (isLeft) parent.left = left
          else parent.right = left
          left.parent = parent
          parent.fix(op) // TODO error! remove this
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

        // TODO fix this too
        // update height
        height = heightOf(left, right)

        // update value
        value = op(left.value, right.value)

        if (parent != null) parent.fix(op)
        else this
      }

      result
    }
    def toString(indent: Int) = " " * indent + s"Inner($height, \n${left.toString(indent + 2)}, \n${right.toString(indent + 2)})"
  }

  class Leaf[@spec(Int, Long, Double) T](val source: Value[T], var parent: Inner[T]) extends Node[T] {
    def height = 0
    def value = source()
    def refresh(op: (T, T) => T) {
      if (parent != null) parent.refresh(op)
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
    def toString(indent: Int) = " " * indent + s"Leaf($source)"
  }

  class Empty[@spec(Int, Long, Double) T](val value: T) extends Node[T] {
    def height = 0
    def parent = null
    def parent_=(p: Inner[T]) = throw new IllegalStateException
    def refresh(op: (T, T) => T) {}
    def insert(leaf: Leaf[T], op: (T, T) => T) = leaf
    def toString(indent: Int) = " " * indent + s"Empty($value)"
  }
}