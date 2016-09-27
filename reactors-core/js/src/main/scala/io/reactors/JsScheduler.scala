package io.reactors






object JsScheduler {
  lazy val default: Scheduler = ???

  object Key {
    val default = "org.reactors.JsScheduler::default"
    def defaultScheduler = default
  }
}
