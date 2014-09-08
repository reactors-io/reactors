import sbt._
import Keys._
import Process._
import java.io._



object ReactiveCollectionsBuild extends Build {

  def versionFromFile(filename: String): String = {
    val fis = new FileInputStream(filename)
    val props = new java.util.Properties()
    try props.load(fis)
    finally fis.close()

    val major = props.getProperty("reactive_collections_major")
    val minor = props.getProperty("reactive_collections_minor")
    s"$major.$minor"
  }

  val frameworkVersion = versionFromFile("version.conf")

  val rcScalaVersion = "2.10.4"

  val rcCrossScalaVersions = Seq("2.10.4", "2.11.1")

  val reactiveCollectionsSettings = Defaults.defaultSettings ++ Seq (
    name := "reactive-collections",
    version := frameworkVersion,
    organization := "com.storm-enroute",
    scalaVersion := rcScalaVersion,
    crossScalaVersions := rcCrossScalaVersions,
    libraryDependencies <++= (scalaVersion)(sv => dependencies(sv)),
    testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
    parallelExecution in Test := false,
    scalacOptions in (Compile, doc) ++= Seq(
      "-implicits"
    ),
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases"
    ),
    publishMavenStyle := true,
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra :=
      <url>http://reactive-collections.com/</url>
      <licenses>
        <license>
          <name>BSD-style</name>
          <url>http://opensource.org/licenses/BSD-3-Clause</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:storm-enroute/reactive-collections.git</url>
        <connection>scm:git:git@github.com:storm-enroute/reactive-collections.git</connection>
      </scm>
      <developers>
        <developer>
          <id>axel22</id>
          <name>Aleksandar Prokopec</name>
          <url>http://axel22.github.com/</url>
        </developer>
      </developers>
  )

  def dependencies(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, major)) if major >= 11 => Seq(
      "org.scalatest" % "scalatest_2.11" % "2.1.7" % "test",
      "com.storm-enroute" %% "scalameter" % "0.6" % "test",
      "com.netflix.rxjava" % "rxjava-scala" % "0.19.2" % "test",
      "org.scala-lang" % "scala-reflect" % "2.11.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"
    )
    case Some((2, 10)) => Seq(
      "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
      "com.storm-enroute" %% "scalameter" % "0.6" % "test",
      "com.netflix.rxjava" % "rxjava-scala" % "0.19.2" % "test"
    )
    case _ => Nil
  }

  val reactiveCollectionsCoreSettings = Defaults.defaultSettings ++ Seq (
    name := "reactive-collections-core",
    version := frameworkVersion,
    organization := "com.storm-enroute",
    scalaVersion := rcScalaVersion,
    crossScalaVersions := rcCrossScalaVersions,
    libraryDependencies <++= (scalaVersion)(sv => coreDependencies(sv)),
    testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
    parallelExecution in Test := false,
    scalacOptions in (Compile, doc) ++= Seq(
      "-implicits"
    ),
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases"
    ),
    publishMavenStyle := true,
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra :=
      <url>http://reactive-collections.com/</url>
      <licenses>
        <license>
          <name>BSD-style</name>
          <url>http://opensource.org/licenses/BSD-3-Clause</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:storm-enroute/reactive-collections.git</url>
        <connection>scm:git:git@github.com:storm-enroute/reactive-collections.git</connection>
      </scm>
      <developers>
        <developer>
          <id>axel22</id>
          <name>Aleksandar Prokopec</name>
          <url>http://axel22.github.com/</url>
        </developer>
      </developers>
  )

  def coreDependencies(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, major)) if major >= 11 => Seq(
      "org.scalatest" % "scalatest_2.11" % "2.1.7" % "test",
      "org.scalacheck" %% "scalacheck" % "1.11.3" % "test",
      "com.storm-enroute" %% "scalameter" % "0.6" % "test",
      "org.scala-lang" % "scala-reflect" % "2.11.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"
    )
    case Some((2, 10)) => Seq(
      "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
      "org.scalacheck" %% "scalacheck" % "1.11.3" % "test",
      "com.storm-enroute" %% "scalameter" % "0.6" % "test"
    )
    case _ => Nil
  }

  lazy val reactiveCollectionsCore = Project(
    "reactive-collections-core",
    file("reactive-collections-core"),
    settings = reactiveCollectionsCoreSettings
  )

  lazy val reactiveCollections = Project(
    "reactive-collections",
    file("."),
    settings = reactiveCollectionsSettings
  ) dependsOn (
    reactiveCollectionsCore
  )

}
