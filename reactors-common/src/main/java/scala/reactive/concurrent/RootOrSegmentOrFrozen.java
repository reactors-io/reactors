package scala.reactive.core.concurrent;



import static scala.reactive.core.concurrent.unsafe.instance;



abstract class RootOrSegmentOrFrozen<T> {

  public abstract boolean enqueue(T x);

  public abstract Object dequeue();

}
