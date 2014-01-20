
import bintray.Keys._

name := "reactress"

version := "0.0.2-SNAPSHOT"

seq(bintrayPublishSettings:_*)

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

repository in bintray := "generic"

bintrayOrganization in bintray := Some("storm-enroute")
