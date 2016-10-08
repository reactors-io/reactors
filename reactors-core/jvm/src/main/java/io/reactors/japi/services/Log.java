package io.reactors.japi.services;



import io.reactors.japi.ReactorSystem;
import io.reactors.japi.Service;



public class Log extends Service {
  private io.reactors.services.Log rawLog;

  public Log(ReactorSystem system) {
    Class<?> logClass = io.reactors.services.Log.class;
    scala.reflect.ClassTag<io.reactors.services.Log> tag =
      scala.reflect.ClassTag$.MODULE$.<io.reactors.services.Log>apply(logClass);
    this.rawLog = system.rawSystem().<io.reactors.services.Log>service(tag);
  }

  public void info(Object obj) {
    rawLog.apply().apply(obj);
  }
}
