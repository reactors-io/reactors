"use strict";


class Log {
  static error(msg, err) {
    console.log(msg);
    throw new Error(err);
  }
}
