


import java.io._
import org.stormenroute.mecha._
import sbt._
import sbt.Keys._
import sbt.Process._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._



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

  def projectSettings(
    suffix: String, deps: String => Seq[ModuleID],
    defaults: Seq[Setting[_]] = Defaults.defaultSettings
  ) = {
    defaults ++ MechaRepoPlugin.defaultSettings ++ Seq(
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
      fork in run := true,
      cancelable in Global := true,
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
          "https://oss.sonatype.org/content/repositories/releases",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
      ),
      ivyLoggingLevel in ThisBuild := UpdateLogging.Quiet,
      publishMavenStyle := true,      publishTo <<= version { (v: String) =>
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
  }

  val reactorsSettings = projectSettings("", _ => Seq()) ++ Seq(
    (test in Test) <<= (test in Test)
      .dependsOn(test in (reactorsCommon, Test))
      .dependsOn(test in (reactorsCore, Test))
      .dependsOn(test in (reactorsContainer, Test))
      .dependsOn(test in (reactorsRemote, Test))
      .dependsOn(test in (reactorsProtocols, Test))
      .dependsOn(test in (reactorsExtra, Test)),
    publish <<= publish
      .dependsOn(publish in reactorsCommon)
      .dependsOn(publish in reactorsCore)
      .dependsOn(publish in reactorsContainer)
      .dependsOn(publish in reactorsRemote)
      .dependsOn(publish in reactorsProtocols)
      .dependsOn(publish in reactorsExtra),
    libraryDependencies ++= Seq(
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "junit" % "junit" % "4.12" % "test"
    )
  )

  val reactors210Settings = projectSettings("210", _ => Seq()) ++ Seq(
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
        "org.scala-lang" % "scala-reflect" % "2.11.4",
        "com.typesafe.akka" %% "akka-actor" % "2.3.15" % "bench"
      )
      case Some((2, 10)) => Seq(
        "org.scalatest" % "scalatest_2.10" % "2.2.4" % "test",
        "org.scalacheck" %% "scalacheck" % "1.11.4" % "test"
      )
      case _ => Nil
    }

  def gitPropsContents(dir: File, baseDir: File): Seq[File] = {
    def run(cmd: String*): String = Process(cmd, Some(baseDir)).!!
    val branch = run("git", "rev-parse", "--abbrev-ref", "HEAD").trim
    val commitTs = run("git", "--no-pager", "show", "-s", "--format=%ct", "HEAD")
    val sha = run("git", "rev-parse", "HEAD").trim
    val contents = s"""
    {
      "branch": "$branch",
      "commit-timestamp": $commitTs,
      "sha": "$sha"
    }
    """
    val file = dir / "reactors-io" / ".gitprops"
    IO.write(file, contents)
    Seq(file)
  }

  val reactorsCoreSettings = projectSettings("-core", coreDependencies) ++ Seq(
    (test in Test) <<= (test in Test)
      .dependsOn(test in (reactorsCommon, Test)),
    publish <<= publish.dependsOn(publish in reactorsCommon),
    resourceGenerators in Compile <+= (resourceManaged in Compile, baseDirectory) map {
      (dir, baseDir) => gitPropsContents(dir, baseDir)
    }
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

  def reactorsCommonSettings = projectSettings("-common", commonDependencies)

  def commonDependencies(scalaVersion: String) = defaultDependencies(scalaVersion)

  def reactorsContainerSettings = projectSettings("-container", containerDependencies)

  def containerDependencies(scalaVersion: String) = defaultDependencies(scalaVersion)

  def reactorsRemoteSettings = projectSettings("-remote", remoteDependencies)

  def remoteDependencies(scalaVersion: String) = defaultDependencies(scalaVersion)

  def reactorsProtocolsSettings = projectSettings("-protocols", protocolsDependencies)

  def protocolsDependencies(scalaVersion: String) = defaultDependencies(scalaVersion)

  def reactorsDebuggerSettings = projectSettings("-debugger", debuggerDependencies)

  def debuggerDependencies(scalaVersion: String) = {
    val scalaDeps = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, major)) if major >= 11 => Seq(
        "org.scala-lang" % "scala-compiler" % "2.11.7"
      )
      case Some((2, 10)) => Seq(
        "org.scala-lang" % "scala-compiler" % "2.10.4"
      )
      case _ => Nil
    }

    defaultDependencies(scalaVersion) ++ scalaDeps ++ Seq(
      "org.rapidoid" % "rapidoid-http-server" % "5.1.9",
      "org.rapidoid" % "rapidoid-gui" % "5.1.9",
      "com.github.spullara.mustache.java" % "compiler" % "0.9.2",
      "commons-io" % "commons-io" % "2.4",
      "org.json4s" %% "json4s-jackson" % "3.4.0",
      "org.seleniumhq.selenium" % "selenium-java" % "2.53.1",
      "org.seleniumhq.selenium" % "selenium-chrome-driver" % "2.53.1"
    )
  }

  def reactorsScalaJSSettings = {
    projectSettings("-scalajs", scalaJSDependencies, Seq()) ++ Seq(
      fork in Test := false,
      fork in run := false,
      unmanagedSourceDirectories in Compile +=
        baseDirectory.value / ".." / "reactors-common" / "src" / "main" / "scala",
      unmanagedSourceDirectories in Compile +=
        baseDirectory.value / ".." / "reactors-core" / "src" / "main" / "scala",
      libraryDependencies ++= Seq(
      )
    )
  }

  def scalaJSDependencies(scalaVersion: String): Seq[ModuleID] = Seq()

  def reactorsExtraSettings = projectSettings("-extra", extraDependencies)

  def extraDependencies(scalaVersion: String) = {
    val extraDeps = Nil
    defaultDependencies(scalaVersion) ++ extraDeps
  }

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
    reactorsProtocols,
    reactorsDebugger,
    reactorsExtra
  ) dependsOn(
    reactorsCommon % "compile->compile;test->test",
    reactorsCore % "compile->compile;test->test",
    reactorsContainer % "compile->compile;test->test",
    reactorsRemote % "compile->compile;test->test",
    reactorsProtocols % "compile->compile;test->test",
    reactorsDebugger % "compile->compile;test->test",
    reactorsExtra % "compile->compile;test->test"
  ) dependsOnSuperRepo

  lazy val reactors210: Project = Project(
    "reactors210",
    file("reactors210"),
    settings = reactors210Settings
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

  lazy val reactorsScalaJS = Project(
    "reactors-scalajs",
    file("reactors-scalajs"),
    settings = reactorsScalaJSSettings
  ) enablePlugins(ScalaJSPlugin) dependsOn(
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

  lazy val reactorsDebugger: Project = Project(
    "reactors-debugger",
    file("reactors-debugger"),
    settings = reactorsDebuggerSettings
  ) configs(
    Benchmarks
  ) settings(
    inConfig(Benchmarks)(Defaults.testSettings): _*
  ) dependsOn(
    reactorsCommon % "compile->compile;test->test",
    reactorsCore % "compile->compile;test->test"
  ) dependsOnSuperRepo

  lazy val reactorsExtra: Project = Project(
    "reactors-extra",
    file("reactors-extra"),
    settings = reactorsExtraSettings
  ) configs(
    Benchmarks
  ) settings(
    inConfig(Benchmarks)(Defaults.testSettings): _*
  ) dependsOn(
    reactorsCore % "compile->compile;test->test",
    reactorsProtocols % "compile->compile;test->test"
  ) dependsOnSuperRepo

}
