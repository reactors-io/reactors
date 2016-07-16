"use strict";

class ShellAdapter {
  constructor(uid) {
    this.uid = uid;
  }

  eval(api, cmd) {
    return api.replEval(this.uid, cmd);
  }
}
