package scala.reactive.common.concurrent;



import static scala.reactive.common.concurrent.unsafe.instance;



abstract class RootOrSegmentOrFrozen<T> {

  public abstract boolean enqueue(T x);

  public abstract Object dequeue();

}
