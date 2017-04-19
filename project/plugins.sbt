resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
    url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
        Resolver.ivyStylePatterns)

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases"
)

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC1")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.3.2")

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")

//addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.15")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")
