import sbt._
import Keys._
import Process._
import java.io.File



object ReactressBuild extends Build {

  val reactressSettings = Defaults.defaultSettings ++ Seq (
    organization := "org.reactress",
    version := "0.0.1",
    scalaVersion := "2.10.2",
    libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1"
  )

  lazy val reactress = Project(
    "reactress",
    file("."),
    settings = reactressSettings
  )

}