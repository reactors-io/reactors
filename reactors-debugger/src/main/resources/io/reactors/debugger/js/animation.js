"use strict";


class Interpolator {
  map(f) {
    return {
      hasNext: () => {
        return this.hasNext();
      },
      next: () => {
        return f(this.next());
      }
    }
  }
}


class LinearInterpolator extends Interpolator {
  constructor(totalFrames, from, end) {
    super();
    this.totalFrames = totalFrames;
    this.from = from;
    this.end = end;
    this.frame = 0;
  }

  hasNext() {
    return this.frame < this.totalFrames;
  }

  next() {
    var v = this.from + (this.end - this.from) * (this.frame / this.totalFrames);
    this.frame += 1;
    return v;
  }

  stop() {
    this.frame = this.totalFrames;
  }
}


class Animation {
  constructor(interpolator, onStart, setter, onComplete) {
    this.interpolator = interpolator;
    this.onStart = onStart;
    this.setter = setter;
    this.onComplete = onComplete;
  }

  nextFrame() {
    if (this.onStart) {
      this.onStart();
      this.onStart = null;
    }
    if (this.interpolator.hasNext()) {
      var v = this.interpolator.next();
      this.setter(v);
      return true;
    } else {
      this.stop();
      return false;
    }
  }

  stop() {
    if (this.onComplete) {
      this.onComplete();
      this.onComplete = null;
    }
  }
}


class Animator {
  constructor() {
    this.totalAnimations = 0;
    this.maxAnimations = 32;
    this.labeledAnimations = {};
    this.generator = new UidGenerator();
  }

  start(ani) {
    var frame = 0;
    var nextFrame = () => {
      if (ani.nextFrame()) {
        setTimeout(nextFrame, 30);
      }
    };
    nextFrame();
  }

  startBudgeted(ani) {
    if (this.totalAnimations < this.maxAnimations) {
      this.totalAnimations += 1;
      var originalOnComplete = ani.onComplete;
      ani.onComplete = () => {
        this.totalAnimations -= 1;
        originalOnComplete();
      };
      this.start(ani);
    } else {
      ani.onStart();
      ani.onComplete();
    }
  }

  startLabeled(label, ani) {
    var originalOnComplete = ani.onComplete();
    ani.onComplete = () => {
      var currAni = this.labeledAnimations[label];
      if (currAni === ani) {
        delete this.labeledAnimations[label];
        originalOnComplete();
      }
    }
    if (this.labeledAnimations[label]) {
      this.labeledAnimations[label].stop();
    }
    this.labeledAnimations[label] = ani;
    start(ani);
  }
}


var animation = {
  linear: (total, from, end, onStart, setter, onComplete) => {
    var i = new LinearInterpolator(total, from, end);
    return new Animation(i, onStart, setter, onComplete);
  }
};
