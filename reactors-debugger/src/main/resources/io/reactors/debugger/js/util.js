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
var Uid = new UidGenerator();


function startAnimation(totalFrames, from, end, setter, onComplete) {
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
};


var totalAnimations = 0;
var maxAnimations = 32;
function startBudgetAnimation(totalFrames, from, end, onStart, setter, onComplete) {
  if (totalAnimations < maxAnimations) {
    totalAnimations += 1;
    onStart();
    startAnimation(totalFrames, from, end, setter, () => {
      totalAnimations -= 1;
      onComplete();
    });
  } else {
    onStart();
    onComplete();
  }
}
