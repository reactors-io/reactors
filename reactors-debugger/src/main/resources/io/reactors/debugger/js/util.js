"use strict";


class Log {
  static error(msg, err) {
    console.log(msg);
    throw new Error(err);
  }
}


class UidGenerator {
  constructor() {
    this.count = 0;
  }

  num() {
    var uid = this.count;
    this.count += 1;
    return uid;
  }
}


var Uid = new UidGenerator();
