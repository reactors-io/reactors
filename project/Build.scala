import sbt._
import Keys._
import Process._
import java.io.File



object ReactiveCollectionsBuild extends Build {

  val publishUser = "SONATYPE_USER"
  
  val publishPass = "SONATYPE_PASS"

  val frameworkVersion = "0.5-SNAPSHOT"
  
  val userPass = for {
    user <- sys.env.get(publishUser)
    pass <- sys.env.get(publishPass)
  } yield (user, pass)

  val publishCreds: Seq[Setting[_]] = Seq(userPass match {
    case Some((user, pass)) =>
      credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
    case None =>
      // prevent publishing
      publish <<= streams.map(_.log.info("Publishing to Sonatype is disabled since the \"" + publishUser + "\" and/or \"" + publishPass + "\" environment variables are not set."))
  })

  val reactiveCollectionsSettings = Defaults.defaultSettings ++ publishCreds ++ Seq (
    name := "reactive-collections",
    version := frameworkVersion,
    organization := "com.storm-enroute",
    scalaVersion := "2.10.4",
    crossScalaVersions := Seq("2.10.4", "2.11.1"),
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
    case Some((2,11)) => Seq(
      "org.scalatest" % "scalatest_2.11" % "2.1.7" % "test",
      "com.github.axel22" %% "scalameter" % "0.5-M2" % "test",
      "com.netflix.rxjava" % "rxjava-scala" % "0.15.0" % "test",
      "org.scala-lang" % "scala-reflect" % "2.11.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"
    )
    case Some((2,10)) => Seq(
      "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
      "com.github.axel22" %% "scalameter" % "0.5-M2" % "test",
      "com.netflix.rxjava" % "rxjava-scala" % "0.15.0" % "test"
    )
    case _ => Nil
  }

  val reactiveCollectionsCoreSettings = Defaults.defaultSettings ++ publishCreds ++ Seq (
    name := "reactive-collections-core",
    version := frameworkVersion,
    organization := "com.storm-enroute",
    scalaVersion := "2.10.4",
    crossScalaVersions := Seq("2.10.4", "2.11.1"),
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
    case Some((2,11)) => Seq(
      "org.scalatest" % "scalatest_2.11" % "2.1.7" % "test",
      "org.scalacheck" %% "scalacheck" % "1.11.3" % "test",
      "com.github.axel22" %% "scalameter" % "0.5-M2" % "test",
      "org.scala-lang" % "scala-reflect" % "2.11.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"
    )
    case Some((2,10)) => Seq(
      "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
      "org.scalacheck" %% "scalacheck" % "1.11.3" % "test",
      "com.github.axel22" %% "scalameter" % "0.5-M2" % "test"
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
