package io.reactors.japi;






class ReactorSystem {
  public static ReactorSystem create(String name) {
    return new ReactorSystem(name);
  }

  private io.reactors.ReactorSystem proxy;

  private ReactorSystem(String name) {
    this.proxy = new io.reactors.ReactorSystem(
      name, io.reactors.ReactorSystem.defaultBundle());
  }

  public <T> Channel<T> spawn() {
    // TODO: Implement.
    return null;
  }
}
