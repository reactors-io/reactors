package io.reactors.japi;






public class Proto<T> {
  public static <T, I extends Reactor<T>> Proto<T> create(Class<I> cls, Object... as) {
    return new Proto(cls, as);
  }

  public static class ProxyReactor<T> extends io.reactors.Reactor.Abstract<T> {
    public ProxyReactor(Class<?> cls, Object... as) {
      try {
        io.reactors.common.Reflect.instantiate(cls, as);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private io.reactors.Proto<io.reactors.Reactor<T>> self;

  io.reactors.Proto<io.reactors.Reactor<T>> getOriginal() {
    return self;
  }

  private Proto(Class<?> cls, Object... as) {
    Object[] args = new Object[] {cls, as};
    this.self = io.reactors.Proto.apply(ProxyReactor.class, args);
  }
}
