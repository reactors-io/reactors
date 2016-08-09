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
    this.labelCounts = {};
  }

  num() {
    return this.count++;
  }

  labelNum(label) {
    if (!(label in this.labelCounts)) {
      this.labelCounts[label] = 0;
    }
    return this.labelCounts[label]++;
  }
}


class Animator {
  constructor() {
    this.totalAnimations = 0;
    this.maxAnimations = 32;
    this.namedAnimations = {};
  }

  startAnimation(totalFrames, from, end, setter, onComplete) {
    var frame = 0;
    var nextFrame = () => {
      if (frame <= totalFrames) {
        setter(from + (end - from) * (frame / totalFrames));
        frame += 1;
        setTimeout(nextFrame, 30);
      } else {
        onComplete();
      }
    };
    nextFrame();
  }

  startBudgetAnimation(totalFrames, from, end, onStart, setter, onComplete) {
    if (this.totalAnimations < this.maxAnimations) {
      this.totalAnimations += 1;
      onStart();
      this.startAnimation(totalFrames, from, end, setter, () => {
        this.totalAnimations -= 1;
        onComplete();
      });
    } else {
      onStart();
      onComplete();
    }
  }
}


var Uid = new UidGenerator();
