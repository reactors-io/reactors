package io.reactors.japi;






public class Proto<T> {
  public static <T, I extends Reactor<T>> Proto<Reactor<T>> create(Class<I> cls) {
    return new Proto(cls);
  }

  public static class ProxyReactor<T> extends io.reactors.Reactor.Abstract<T> {
    public ProxyReactor(Class<?> cls) {
      try {
        cls.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private io.reactors.Proto<io.reactors.Reactor<T>> self;

  io.reactors.Proto<io.reactors.Reactor<T>> getOriginal() {
    return self;
  }

  private Proto(Class<?> cls) {
    Object[] args = new Object[] {cls};
    this.self = io.reactors.Proto.apply(ProxyReactor.class, args);
  }
}
