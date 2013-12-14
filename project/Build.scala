import sbt._
import Keys._
import Process._
import java.io.File



object ReactressBuild extends Build {

  val reactressSettings = Defaults.defaultSettings ++ Seq (
    organization := "org.reactress",
    version := "0.0.1",
    scalaVersion := "2.10.2",
    libraryDependencies ++= Seq(
      "org.scalatest" % "scalatest_2.10" % "1.9.1",
      "com.github.axel22" %% "scalameter" % "0.4",
      "com.netflix.rxjava" % "rxjava-scala" % "0.15.0"
    ),
    testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework")
  )

  lazy val reactress = Project(
    "reactress",
    file("."),
    settings = reactressSettings
  )

}