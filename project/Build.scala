


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

  def projectSettings(suffix: String, deps: String => Seq[ModuleID]) =
    Defaults.defaultSettings ++
    MechaRepoPlugin.defaultSettings ++ Seq(
      name := s"reactors$suffix",
      version <<= frameworkVersion,
      organization := "com.storm-enroute",
      scalaVersion <<= reactorsScalaVersion,
      crossScalaVersions <<= reactorsCrossScalaVersions,
      libraryDependencies <++= (scalaVersion)(sv => deps(sv)),
      libraryDependencies ++= superRepoDependencies(s"reactors$suffix"),
      testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
      parallelExecution in Test := false,
      parallelExecution in ThisBuild := false,
      concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
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
        "-minSuccessfulTests", "200",
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
      mechaPublishKey := { publish.value },
      mechaDocsRepoKey := "git@github.com:storm-enroute/apidocs.git",
      mechaDocsBranchKey := "gh-pages",
      mechaDocsPathKey := "reactors"
    )

  val reactorsSettings = projectSettings("", _ => Seq()) ++ Seq(
    (test in Test) <<= (test in Test)
      .dependsOn(test in (reactorsCommon, Test))
      .dependsOn(test in (reactorsCore, Test))
      .dependsOn(test in (reactorsContainer, Test))
      .dependsOn(test in (reactorsRemote, Test))
      .dependsOn(test in (reactorsProtocols, Test)),
    publish <<= publish
      .dependsOn(publish in reactorsCommon)
      .dependsOn(publish in reactorsCore)
      .dependsOn(publish in reactorsContainer)
      .dependsOn(publish in reactorsRemote)
      .dependsOn(publish in reactorsProtocols)
  )

  def defaultDependencies(scalaVersion: String): Seq[ModuleID] =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, major)) if major >= 11 => Seq(
        "org.scalatest" % "scalatest_2.11" % "2.2.6" % "test",
        "org.scalacheck" %% "scalacheck" % "1.11.4" % "test",
        "org.scala-lang" % "scala-reflect" % "2.11.1"
      )
      case Some((2, 10)) => Seq(
        "org.scalatest" % "scalatest_2.10" % "2.2.4" % "test",
        "org.scalacheck" %% "scalacheck" % "1.11.4" % "test"
      )
      case _ => Nil
    }

  val reactorsCoreSettings = projectSettings("-core", coreDependencies) ++ Seq(
    (test in Test) <<= (test in Test)
      .dependsOn(test in (reactorsCommon, Test)),
    publish <<= publish.dependsOn(publish in reactorsCommon)
  )

  def coreDependencies(scalaVersion: String) = {
    val extraDeps = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, major)) if major >= 11 => Seq(
        "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1",
        "com.typesafe" % "config" % "1.2.1",
        "commons-io" % "commons-io" % "2.4"
      )
      case Some((2, 10)) => Seq(
        "org.scalatest" % "scalatest_2.10" % "2.2.4" % "test",
        "org.scalacheck" %% "scalacheck" % "1.11.4" % "test",
        "com.typesafe" % "config" % "1.2.1",
        "commons-io" % "commons-io" % "2.4"
      )
      case _ => Nil
    }
    defaultDependencies(scalaVersion) ++ extraDeps
  }

  val reactorsCommonSettings = projectSettings("-common", commonDependencies)

  def commonDependencies(scalaVersion: String) = defaultDependencies(scalaVersion)

  val reactorsContainerSettings = projectSettings("-container", containerDependencies)

  def containerDependencies(scalaVersion: String) = defaultDependencies(scalaVersion)

  def reactorsRemoteSettings = projectSettings("-remote", remoteDependencies)

  def remoteDependencies(scalaVersion: String) = defaultDependencies(scalaVersion)

  def reactorsProtocolsSettings = projectSettings("-protocols", protocolsDependencies)

  def protocolsDependencies(scalaVersion: String) = defaultDependencies(scalaVersion)

  lazy val Benchmarks = config("bench") extend (Test)

  lazy val reactors: Project = Project(
    "reactors",
    file("."),
    settings = reactorsSettings
  ) aggregate(
    reactorsCommon,
    reactorsCore,
    reactorsContainer,
    reactorsRemote,
    reactorsProtocols
  ) dependsOn(
    reactorsCommon % "compile->compile;test->test",
    reactorsCore % "compile->compile;test->test",
    reactorsContainer % "compile->compile;test->test",
    reactorsRemote % "compile->compile;test->test",
    reactorsProtocols % "compile->compile;test->test"
  ) dependsOnSuperRepo

  lazy val reactorsCommon = Project(
    "reactors-common",
    file("reactors-common"),
    settings = reactorsCommonSettings
  ) configs(
    Benchmarks
  ) settings(
    inConfig(Benchmarks)(Defaults.testSettings): _*
  ) dependsOnSuperRepo

  lazy val reactorsCore: Project = Project(
    "reactors-core",
    file("reactors-core"),
    settings = reactorsCoreSettings
  ) configs(
    Benchmarks
  ) settings(
    inConfig(Benchmarks)(Defaults.testSettings): _*
  ) dependsOn(
    reactorsCommon % "compile->compile;test->test"
  ) dependsOnSuperRepo

  lazy val reactorsContainer: Project = Project(
    "reactors-container",
    file("reactors-container"),
    settings = reactorsContainerSettings
  ) configs(
    Benchmarks
  ) settings(
    inConfig(Benchmarks)(Defaults.testSettings): _*
  ) dependsOn(
    reactorsCore % "compile->compile;test->test"
  ) dependsOnSuperRepo

  lazy val reactorsRemote: Project = Project(
    "reactors-remote",
    file("reactors-remote"),
    settings = reactorsRemoteSettings
  ) configs(
    Benchmarks
  ) settings(
    inConfig(Benchmarks)(Defaults.testSettings): _*
  ) dependsOn(
    reactorsCore % "compile->compile;test->test"
  ) dependsOnSuperRepo

  lazy val reactorsProtocols: Project = Project(
    "reactors-protocols",
    file("reactors-protocols"),
    settings = reactorsProtocolsSettings
  ) configs(
    Benchmarks
  ) settings(
    inConfig(Benchmarks)(Defaults.testSettings): _*
  ) dependsOn(
    reactorsCommon % "compile->compile;test->test",
    reactorsCore % "compile->compile;test->test",
    reactorsContainer % "compile->compile;test->test"
  ) dependsOnSuperRepo

}
