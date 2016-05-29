package io.reactors.japi;






class Proto<T> {
  public static <T, I extends Reactor<T>> Proto<Reactor<T>> create(Class<I> cls) {
    return new Proto(cls);
  }

  private io.reactors.Proto<io.reactors.Reactor<T>> proxy;

  io.reactors.Proto<io.reactors.Reactor<T>> getProxy() {
    return proxy;
  }

  private Proto(Class<?> cls) {
    this.proxy = io.reactors.Proto.apply(cls);
  }
}
