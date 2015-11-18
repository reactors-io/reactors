


import sbt._
import Keys._
import Process._
import java.io._
import org.stormenroute.mecha._



object ReactorsBuild extends MechaRepoBuild {

  def repoName = "reactors"

  val frameworkVersion = Def.setting {
    ConfigParsers.versionFromFile(
        (baseDirectory in reactors).value / "version.conf",
        List("reactors_major", "reactors_minor"))
  }

  val reactorsCrossScalaVersions = Def.setting {
    val dir = (baseDirectory in reactors).value
    val path = dir + File.separator + "cross.conf"
    scala.io.Source.fromFile(path).getLines.filter(_.trim != "").toSeq
  }

  val reactorsScalaVersion = Def.setting {
    reactorsCrossScalaVersions.value.head
  }

  val reactorsSettings = Defaults.defaultSettings ++
    MechaRepoPlugin.defaultSettings ++ Seq(
    name := "reactors",
    version <<= frameworkVersion,
    organization := "com.storm-enroute",
    scalaVersion <<= reactorsScalaVersion,
    crossScalaVersions <<= reactorsCrossScalaVersions,
    libraryDependencies <++= (scalaVersion)(sv => dependencies(sv)),
    testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
    parallelExecution in Test := false,
    fork in Test := true,
    javaOptions in test += "-Xmx2G -XX:MaxPermSize=384m",
    scalacOptions ++= Seq(
      "-deprecation"
    ),
    scalacOptions in (Compile, doc) ++= Seq(
      "-implicits"
    ),
    testOptions in Test += Tests.Argument(
      TestFrameworks.ScalaCheck,
      "-minSuccessfulTests", "150",
      "-workers", "1",
      "-verbosity", "2"),
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at
        "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Releases" at
        "https://oss.sonatype.org/content/repositories/releases"
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
      <url>http://reactors.io/</url>
      <licenses>
        <license>
          <name>BSD-style</name>
          <url>http://opensource.org/licenses/BSD-3-Clause</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:reactors-io/reactors.git</url>
        <connection>
          scm:git:git@github.com:reactors-io/reactors.git
        </connection>
      </scm>
      <developers>
        <developer>
          <id>axel22</id>
          <name>Aleksandar Prokopec</name>
          <url>http://axel22.github.com/</url>
        </developer>
      </developers>,
    (test in Test) <<= (test in Test)
      .dependsOn(test in (reactorsCommon, Test)),
    publish <<= publish.dependsOn(publish in reactorsCommon),
    mechaPublishKey := { publish.value },
    mechaDocsRepoKey := "git@github.com:storm-enroute/apidocs.git",
    mechaDocsBranchKey := "gh-pages",
    mechaDocsPathKey := "reactors"
  )

  def dependencies(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, major)) if major >= 11 => Seq(
      "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
      "org.scalacheck" %% "scalacheck" % "1.11.4" % "test",
      "com.netflix.rxjava" % "rxjava-scala" % "0.19.2" % "test",
      "org.scala-lang" % "scala-reflect" % "2.11.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1",
      "com.typesafe" % "config" % "1.2.1",
      "commons-io" % "commons-io" % "2.4"
    )
    case Some((2, 10)) => Seq(
      "org.scalatest" % "scalatest_2.10" % "2.2.4" % "test",
      "org.scalacheck" %% "scalacheck" % "1.11.4" % "test",
      "com.netflix.rxjava" % "rxjava-scala" % "0.19.2" % "test",
      "com.typesafe" % "config" % "1.2.1",
      "commons-io" % "commons-io" % "2.4"
    )
    case _ => Nil
  }

  val reactorsCommonSettings = Defaults.defaultSettings ++ Seq (
    name := "reactors-common",
    version <<= frameworkVersion,
    organization := "com.storm-enroute",
    scalaVersion <<= reactorsScalaVersion,
    crossScalaVersions <<= reactorsCrossScalaVersions,
    libraryDependencies <++= (scalaVersion)(sv => coreDependencies(sv)),
    testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
    parallelExecution in Test := false,
    fork in Test := true,
    scalacOptions ++= Seq(
      "-deprecation"
    ),
    scalacOptions in (Compile, doc) ++= Seq(
      "-implicits"
    ),
    testOptions in Test += Tests.Argument(
      TestFrameworks.ScalaCheck,
      "-minSuccessfulTests", "150",
      "-workers", "1",
      "-verbosity", "2"),
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at
        "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Releases" at
        "https://oss.sonatype.org/content/repositories/releases"
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
      <url>http://reactors.io/</url>
      <licenses>
        <license>
          <name>BSD-style</name>
          <url>http://opensource.org/licenses/BSD-3-Clause</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:reactors-io/reactors.git</url>
        <connection>
          scm:git:git@github.com:reactors-io/reactors.git
        </connection>
      </scm>
      <developers>
        <developer>
          <id>axel22</id>
          <name>Aleksandar Prokopec</name>
          <url>http://axel22.github.com/</url>
        </developer>
      </developers>,
    mechaDocsRepoKey := "git@github.com:storm-enroute/apidocs.git",
    mechaDocsBranchKey := "gh-pages",
    mechaDocsPathKey := "reactors-common"
  )

  def coreDependencies(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, major)) if major >= 11 => Seq(
      "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
      "org.scalacheck" %% "scalacheck" % "1.11.4" % "test",
      "org.scala-lang" % "scala-reflect" % "2.11.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"
    )
    case Some((2, 10)) => Seq(
      "org.scalatest" % "scalatest_2.10" % "2.2.4" % "test",
      "org.scalacheck" %% "scalacheck" % "1.11.4" % "test"
    )
    case _ => Nil
  }

  lazy val Benchmarks = config("bench") extend (Test)

  lazy val reactorsCommon = Project(
    "reactors-common",
    file("reactors-common"),
    settings = reactorsCommonSettings
  ) configs(
    Benchmarks
  ) settings(
    inConfig(Benchmarks)(Defaults.testSettings): _*
  ) dependsOnSuperRepo

  lazy val reactors: Project = Project(
    "reactors",
    file("."),
    settings = reactorsSettings
  ) configs(
    Benchmarks
  ) dependsOn(
    reactorsCommon % "compile->compile;test->test"
  ) dependsOnSuperRepo

}
