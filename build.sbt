
// set environment variables to publish
// in newer SBT versions, this apparently has to go to `build.sbt`

{
  val publishUser = "SONATYPE_USER"
  val publishPass = "SONATYPE_PASS"
  val userPass = for {
    user <- sys.env.get(publishUser)
    pass <- sys.env.get(publishPass)
  } yield (user, pass)
  val publishCreds: Seq[Setting[_]] = Seq(userPass match {
    case Some((user, pass)) =>
      println(s"Username and password for Sonatype picked up: '$user', '${if (pass != "") "******" else ""}'")
      credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
    case None =>
      // prevent publishing
      val errorMessage =
        "Publishing to Sonatype is disabled since the \"" +
        publishUser + "\" and/or \"" + publishPass + "\" environment variables are not set."
      println(errorMessage)
      publish <<= streams.map(_.log.info(errorMessage))
  })
  publishCreds
}
