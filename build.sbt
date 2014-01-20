
import bintray.Keys._

name := "reactress"

seq(bintrayPublishSettings:_*)

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

repository in bintray := "maven"

bintrayOrganization in bintray := Some("storm-enroute")
