"use-strict";


class EventBus {
  constructor() {
    this.uidCount = 0;
    this.observers = {};
  }

  observe(key, f) {
    if (!(key in this.observers)) {
      this.observers[key] = {};
    }

    var obs = this.observers[key];
    var uid = this.uidCount++;
    obs[uid] = f;

    return () => {
      delete obs[uid];
    }
  }

  post(key, event) {
    if (key in this.observers) {
      var obs = this.observers[key];
      for (var uid in obs) {
        var f = obs[uid];
        f(event);
      }
    }
  }
}
