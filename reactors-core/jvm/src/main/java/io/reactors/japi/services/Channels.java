package io.reactors.japi.services;



import io.reactors.japi.ReactorSystem;
import io.reactors.japi.Service;



public class Channels extends Service {
  private io.reactors.services.Channels rawChannels;

  public Channels(ReactorSystem system) {
    this.rawChannels = getRawService(system, io.reactors.services.Channels.class);
  }

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
