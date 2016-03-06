package io.reactors.common.concurrent;



import static io.reactors.common.concurrent.unsafe.instance;



abstract class RootOrSegmentOrFrozen<T> {

  public abstract boolean enqueue(T x);

  public abstract Object dequeue();

}
