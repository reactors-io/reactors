package io.reactors.japi;






public class ChannelBuilder {
  private io.reactors.ChannelBuilder self;

  ChannelBuilder(io.reactors.ChannelBuilder self) {
    this.self = self;
  }

  public ChannelBuilder daemon() {
    return new ChannelBuilder(self.daemon());
  }

  public <Q> Connector<Q> open() {
    return new Connector<Q>(self.open(Util.<Q>arrayable()));
  }
}
